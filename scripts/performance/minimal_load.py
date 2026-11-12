#!/usr/bin/env python3
"""Small, paced A/B benchmark. Own containers/data only; never targets a live URL.

Build the current API first. Requires Docker, Python 3 and openssl, no Python packages.
Run from anywhere: python3 scripts/performance/minimal_load.py --output /tmp/carabo-load
"""
import argparse
import base64
import concurrent.futures
from contextlib import closing
import datetime as dt
import hashlib
import http.client
import json
import math
import os
from pathlib import Path
import statistics
import subprocess
import tempfile
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
BASELINE = "ghcr.io/htm4rck/carabo:244d2c6974d202c8844e5ec3c920a2baf86ed7e2"
MIB = 1024 * 1024


def run(*args, data=None, check=True):
    result = subprocess.run(args, input=data, capture_output=True, timeout=120)
    if check and result.returncode:
        # Command/environment can contain disposable credentials; do not print them.
        raise RuntimeError(f"{args[0]} {args[1]} failed: " + result.stderr.decode()[-500:])
    return result.stdout.decode().strip()


def host_guard():
    values = {line.split(':')[0]: int(line.split()[1]) for line in Path('/proc/meminfo').read_text().splitlines() if line.split()[1].isdigit()}
    if values['MemAvailable'] < 3 * 1024 * 1024:
        raise RuntimeError('Stopped: host available memory below 3 GiB')
    if values.get('SwapFree', 0) < values.get('SwapTotal', 0) - 256 * 1024:
        raise RuntimeError('Stopped: host is using more than 256 MiB swap')
    if os.getloadavg()[0] > (os.cpu_count() or 1) * .8:
        raise RuntimeError('Stopped: host load exceeds 80% of logical CPU count')
    return {'available_mib': round(values['MemAvailable'] / 1024), 'load1': os.getloadavg()[0]}


def percentile(values, quantile):
    values = sorted(values)
    return values[max(0, math.ceil(len(values) * quantile) - 1)] if values else None


class Benchmark:
    def __init__(self, output, rounds):
        self.output = output
        self.rounds = rounds
        self.prefix = 'carabo-load-' + uuid.uuid4().hex[:8]
        self.containers = []
        self.app = None
        self.network = None
        self.rows = []
        self.monitored = []
        self.result = {'started_utc': dt.datetime.now(dt.timezone.utc).isoformat(), 'baseline_image': BASELINE,
            'source_head': run('git', '-C', str(ROOT), 'rev-parse', 'HEAD'), 'dataset': {'documents_a': 3000, 'documents_b': 300, 'producers_a': 20},
            'limits': {'app_cpus': 1, 'app_memory_mib': 768, 'heap_mib': 384, 'db_cpus': .5, 'db_memory_mib': 256,
                       'rabbit_cpus': .25, 'rabbit_memory_mib': 256, 'max_clients': 4, 'max_read_rps': 4, 'max_excel_clients': 1},
            'host_before': host_guard(), 'samples': self.rows, 'rounds': self.monitored}
        artifacts = ROOT / 'apps/api/carabo-starter-quarkus/build/quarkus-app'
        self.result['current_artifacts_sha256'] = {str(path.relative_to(artifacts)): hashlib.sha256(path.read_bytes()).hexdigest()
            for path in list((artifacts/'app').glob('*.jar')) + list((artifacts/'lib/main').glob('io.carabo.*.jar'))}
        self.result['baseline_image_id'] = run('docker','image','inspect',BASELINE,'--format','{{.Id}}')

    def start_container(self, suffix, image, arguments, command=()):
        name = self.prefix + '-' + suffix
        run('docker', 'run', '--rm', '-d', '--name', name, '--label', 'carabo.performance=isolated', '--network', self.network,
            *arguments, image, *command)
        self.containers.append(name)
        return name

    def sql(self, sql):
        return run('docker', 'exec', '-i', self.pg, 'psql', '-U', 'postgres', '-d', 'load_test', '-v', 'ON_ERROR_STOP=1', '-At', data=sql.encode())

    def setup(self, temporary):
        self.temporary = temporary
        self.password = uuid.uuid4().hex
        self.network = self.prefix
        run('docker', 'network', 'create', '--internal', '--label', 'carabo.performance=isolated', self.network)
        self.pg = self.start_container('pg', 'postgres:16-alpine', ['--cpus', '.5', '--memory', '256m', '--memory-swap', '256m',
            '--network-alias', 'pg', '-e', 'POSTGRES_DB=load_test', '-e', 'POSTGRES_PASSWORD=' + self.password],
            ('-c', 'shared_buffers=32MB', '-c', 'max_connections=30', '-c', 'shared_preload_libraries=pg_stat_statements'))
        self.rabbit = self.start_container('rabbit', 'rabbitmq:3-management-alpine', ['--cpus', '.25', '--memory', '256m', '--memory-swap', '256m',
            '--network-alias', 'rabbit', '-e', 'RABBITMQ_DEFAULT_USER=load', '-e', 'RABBITMQ_DEFAULT_PASS=' + self.password,
            '-e', 'RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS=+S 1:1 +A 2'])
        for _ in range(100):
            if 'accepting connections' in run('docker', 'exec', self.pg, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres', check=False): break
            time.sleep(.25)
        else: raise RuntimeError('Test PostgreSQL did not start')
        for migration in sorted((ROOT / 'migrations').glob('*.sql')):
            if not migration.name.startswith('002-'): self.sql(migration.read_text())
        self.key = uuid.uuid4().hex + uuid.uuid4().hex
        self.sql(f"CREATE USER bench WITH PASSWORD '{self.password}'; CREATE EXTENSION pg_stat_statements;" + """
            CREATE SCHEMA auth;
            CREATE TABLE auth.user_business ("userCode" text, "businessCode" text, "isActive" boolean);
            CREATE UNIQUE INDEX ON auth.user_business ("userCode", "businessCode");
            INSERT INTO auth.user_business VALUES ('load-user','LOAD_A',true),('load-user','LOAD_B',true);
            INSERT INTO carabo.tenant (business_code,issuer_ruc,issuer_name,active) VALUES
                ('LOAD_A','20111111111','Synthetic issuer A',true),('LOAD_B','20222222222','Synthetic issuer B',true);
        """)
        self.sql(f"""
            INSERT INTO carabo.producer_integration (id,business_code,code,name,api_key_hash,scopes,status)
            SELECT md5('producer-' || n)::uuid, 'LOAD_A', 'source-' || n, 'Synthetic producer ' || n,
                CASE WHEN n=1 THEN '{hashlib.sha256(self.key.encode()).hexdigest()}' ELSE md5('fixture-key-' || n) || md5('fixture-key-' || n) END,
                'DOCUMENT_READ', 'ACTIVE' FROM generate_series(1,20) n;
            INSERT INTO carabo.document (id,business_code,doc_type,series,number,issue_date,issuer_ruc,issuer_name,receiver_doc_type,receiver_doc,receiver_name,tax_base,igv_amount,total,status,external_id,producer_id,producer_code,created_at,updated_at,sunat_response)
            SELECT md5('document-A-' || n)::uuid,'LOAD_A','INVOICE','F001',n,DATE '2026-01-01' + ((n-1)/100),'20111111111','Synthetic issuer A','6','20999999991','Synthetic customer ' || n,100,18,118,'ACCEPTED','fixture-A-' || n,
                md5('producer-' || (1+((n-1)%20)))::uuid, 'source-' || (1+((n-1)%20)),TIMESTAMPTZ '2026-01-01T00:00:00Z' + n * INTERVAL '1 minute',CURRENT_TIMESTAMP,
                'SUNAT synthetic response ' || repeat(md5(n::text), 32) FROM generate_series(1,3000) n;
            INSERT INTO carabo.document (business_code,doc_type,series,number,issue_date,issuer_ruc,issuer_name,receiver_doc_type,receiver_doc,receiver_name,tax_base,igv_amount,total,status,created_at,updated_at)
            SELECT 'LOAD_B','INVOICE','F001',n,DATE '2026-01-01','20222222222','Synthetic issuer B','6','20999999992','Foreign synthetic customer',200,36,236,'ACCEPTED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM generate_series(1,300) n;
            GRANT USAGE ON SCHEMA carabo,auth TO bench;
            GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA carabo,auth TO bench;
            GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA carabo,auth TO bench;
            ANALYZE carabo.document; ANALYZE carabo.producer_integration;
        """)
        private = temporary / 'private.pem'
        public = temporary / 'public.pem'
        run('openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private))
        run('openssl', 'pkey', '-in', str(private), '-pubout', '-out', str(public))
        enc = lambda value: base64.urlsafe_b64encode(value).rstrip(b'=').decode()
        claims = {'sub':'load-user','iss':'https://load.invalid','aud':'tordo-api','iat':int(time.time()),'exp':int(time.time())+3600,'licenseType':'root'}
        signing = enc(json.dumps({'alg':'RS256','typ':'JWT'}).encode()) + '.' + enc(json.dumps(claims).encode())
        signature = subprocess.run(['openssl','dgst','-sha256','-sign',str(private)],input=signing.encode(),capture_output=True,check=True).stdout
        self.token = signing + '.' + enc(signature)
        for _ in range(120):
            if 'Server startup complete' in run('docker','logs','--tail','20',self.rabbit,check=False): break
            host_guard(); time.sleep(1)
        else: raise RuntimeError('Test RabbitMQ did not start')
        print('Isolated PostgreSQL/RabbitMQ ready; synthetic fixtures seeded', flush=True)

    def headers(self, mode):
        return {'X-Api-Key':self.key} if mode == 'machine' else {'Authorization':'Bearer ' + self.token, 'business-code':'LOAD_A'}

    def request(self, path, mode='human', connection=None):
        own = connection is None
        connection = connection or http.client.HTTPConnection(self.address, self.port, timeout=20)
        started = time.perf_counter()
        try:
            connection.request('GET',path,headers=self.headers(mode))
            response = connection.getresponse(); body = response.read()
            return response.status, body, (time.perf_counter()-started)*1000
        finally:
            if own: connection.close()

    def app_state(self):
        memory = int((self.cgroup / 'memory.current').read_text())
        cpu = dict(line.split() for line in (self.cgroup / 'cpu.stat').read_text().splitlines())
        rss = next(int(line.split()[1])*1024 for line in Path(f'/proc/{self.pid}/status').read_text().splitlines() if line.startswith('VmRSS:'))
        return {'cgroup_memory_bytes':memory, 'rss_bytes':rss, 'cpu_usec':int(cpu['usage_usec']), 'throttled_usec':int(cpu['throttled_usec'])}

    def metrics(self):
        code, raw, _ = self.request('/q/metrics')
        if code != 200: return {'status':code}
        values = {}
        for line in raw.decode().splitlines():
            if not line or line.startswith('#'): continue
            name, value = line.rsplit(' ',1)
            if name.startswith(('jvm_memory_used_bytes','jvm_memory_committed_bytes','jvm_gc_pause_seconds_count','jvm_gc_pause_seconds_sum','jvm_gc_memory_allocated_bytes_total','agroal_active_count','agroal_awaiting_count')):
                values[name] = float(value)
        return values

    def query_stats(self):
        data = self.sql("""SELECT COALESCE(json_agg(q),'[]'::json) FROM (
            SELECT query,calls,round(total_exec_time::numeric,3) AS total_ms,rows,shared_blks_hit,shared_blks_read,wal_bytes
            FROM pg_stat_statements WHERE userid=(SELECT oid FROM pg_roles WHERE rolname='bench') AND toplevel
            ORDER BY total_exec_time DESC) q;""")
        return json.loads(data)

    def load(self, version, round_number, label, path, clients, mode='human', count=8, interval=1):
        host_guard()
        before_metrics = self.metrics()
        self.sql('SELECT pg_stat_statements_reset();')
        before = self.app_state()
        readings, errors, latencies, sizes = [], [], [], []
        stop = threading.Event()
        def monitor():
            while not stop.wait(.25):
                try:
                    state = self.app_state(); readings.append(state)
                    if state['cgroup_memory_bytes'] > 700*MIB: errors.append('Stopped: app memory above 700 MiB'); stop.set()
                    host_guard()
                except Exception as error: errors.append(str(error)); stop.set()
        thread = threading.Thread(target=monitor,daemon=True); thread.start()
        start_wall = dt.datetime.now(dt.timezone.utc).isoformat()
        started = time.perf_counter()
        def client(index):
            with closing(http.client.HTTPConnection(self.address,self.port,timeout=20)) as connection:
                for attempt in range(count):
                    scheduled = started + attempt*interval + index*interval/clients
                    if stop.wait(max(0,scheduled-time.perf_counter())): break
                    try:
                        status, body, latency = self.request(path,mode,connection)
                        latencies.append(latency); sizes.append(len(body))
                        if status != 200: errors.append(f'HTTP {status}: ' + body[:120].decode(errors='replace'))
                    except Exception as error: errors.append(type(error).__name__ + ': ' + str(error))
                    if errors: stop.set(); break
        with concurrent.futures.ThreadPoolExecutor(max_workers=clients) as pool:
            list(pool.map(client,range(clients)))
        duration = time.perf_counter()-started
        stop.set(); thread.join()
        end_wall = dt.datetime.now(dt.timezone.utc).isoformat()
        after = self.app_state()
        queries = self.query_stats()
        row = {'version':version,'round':round_number,'scenario':label,'clients':clients,'auth':mode,
            'target_rps':clients/interval,'requests':len(latencies),'duration_seconds':duration,'errors':errors,
            'latency_ms':latencies,'response_bytes':sizes,'p50_ms':statistics.median(latencies) if latencies else None,'p95_ms':percentile(latencies,.95),
            'cpu_ms':(after['cpu_usec']-before['cpu_usec'])/1000,'throttled_ms':(after['throttled_usec']-before['throttled_usec'])/1000,
            'rss_before_mib':before['rss_bytes']/MIB,'rss_peak_mib':max([before['rss_bytes'],after['rss_bytes']]+[x['rss_bytes'] for x in readings])/MIB,
            'memory_peak_mib':max([before['cgroup_memory_bytes'],after['cgroup_memory_bytes']]+[x['cgroup_memory_bytes'] for x in readings])/MIB,
            'start_utc':start_wall,'end_utc':end_wall,'sql':queries,'metrics_before':before_metrics,'metrics_after':self.metrics()}
        self.rows.append(row)
        self.save()
        print(f"{version} round {round_number} {label} c={clients} n={len(latencies)} p50={row['p50_ms']:.1f}ms p95={row['p95_ms']:.1f}ms errors={len(errors)} rss_peak={row['rss_peak_mib']:.0f}MiB",flush=True)
        if errors: raise RuntimeError('Stopped after a failed request or resource guard: ' + errors[0])

    def execute_round(self, version, round_number):
        host_guard()
        arguments = ['--cpus','1','--memory','768m','--memory-swap','768m','--pids-limit','256',
            '--read-only','--tmpfs','/tmp:rw,size=128m',
            '-v',str(self.temporary / 'public.pem')+':/bench/public.pem:ro','-v',str(self.output)+':/results',
            '-e','DB_URL=jdbc:postgresql://pg:5432/load_test?currentSchema=carabo','-e','DB_USERNAME=bench','-e','DB_PASSWORD='+self.password,
            '-e','RABBITMQ_HOST=rabbit','-e','RABBITMQ_USERNAME=load','-e','RABBITMQ_PASSWORD='+self.password,
            '-e','QUARKUS_FLYWAY_MIGRATE_AT_START=false','-e','QUARKUS_SCHEDULER_ENABLED=false',
            '-e','MP_JWT_VERIFY_PUBLICKEY_LOCATION=file:/bench/public.pem','-e','JWT_ISSUER=https://load.invalid',
            '-e','CARABO_ADMIN_KEY=load-fixture-unused','-e','CARABO_OTEL_DISABLED=true',
            '-e','JAVA_TOOL_OPTIONS=-Xms64m -Xmx384m -XX:ActiveProcessorCount=2 -Djava.awt.headless=true '+
                f'-XX:StartFlightRecording=filename=/results/{version}-{round_number}.jfr,settings=profile,dumponexit=true,maxsize=16m -Xlog:gc:file=/results/{version}-{round_number}-gc.log',
        ]
        if version == 'current': arguments += ['-v',str(ROOT / 'apps/api/carabo-starter-quarkus/build/quarkus-app')+':/app:ro']
        self.app = self.start_container(f'{version}-{round_number}',BASELINE,arguments)
        try:
            # No published ports: the client connects directly to the generated fixture address.
            self.address = run('docker','inspect',self.app,'--format','{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}')
            self.port = 8998
            for _ in range(180):
                host_guard()
                try:
                    if self.request('/health')[0] == 200: break
                except (OSError,http.client.HTTPException): pass
                if run('docker','inspect',self.app,'--format','{{.State.Running}}') != 'true': raise RuntimeError('Test API failed to start')
                time.sleep(.5)
            else: raise RuntimeError('Test API health timed out')
            self.pid = int(run('docker','inspect',self.app,'--format','{{.State.Pid}}'))
            relative = next(line.split(':',2)[2] for line in Path(f'/proc/{self.pid}/cgroup').read_text().splitlines() if line.startswith('0::'))
            self.cgroup = Path('/sys/fs/cgroup') / relative.lstrip('/')
            # Equal warmup: all classes/queries/Excel/font paths before recording comparisons.
            small = '/reports/export/excel?from=2026-01-01&to=2026-01-02'
            large = '/reports/export/excel?from=2026-01-01&to=2026-01-31'
            for _ in range(8):
                for path, mode in [('/integrations','human'),('/reports/sales?from=2026-01-01&to=2026-01-31','human'),('/documents?limit=50','machine')]:
                    if self.request(path,mode)[0] != 200: raise RuntimeError('Warmup failed')
                    time.sleep(.25)
            for path in [small, large]:
                status, body, _ = self.request(path)
                if status != 200 or not body.startswith(b'PK'): raise RuntimeError('Excel warmup failed')
            time.sleep(2)
            self.monitored.append({'version':version,'round':round_number,'warm_state':self.app_state(),'warm_metrics':self.metrics()})
            print(f'{version} round {round_number}: warmed, starting paced requests',flush=True)
            scenarios = [('integrations','/integrations','human'),('sales','/reports/sales?from=2026-01-01&to=2026-01-31','human'),('documents','/documents?limit=50','machine')]
            for label,path,mode in scenarios:
                for clients in (1,4): self.load(version,round_number,label,path,clients,mode)
            self.load(version,round_number,'excel_200',small,1,count=4,interval=2)
            self.load(version,round_number,'excel_3000',large,1,count=4,interval=2)
        finally:
            (self.output / f'{version}-{round_number}-api.log').write_text(run('docker','logs',self.app,check=False))
            run('docker','stop','-t','10',self.app,check=False)
            self.app = None

    def explain(self):
        statements = {
            'documents_page': "SELECT id,business_code,doc_type,series,number,total,receiver_name,sunat_response FROM carabo.document WHERE business_code='LOAD_A' ORDER BY created_at DESC LIMIT 50",
            'sales': "SELECT doc_type,COUNT(*),SUM(total) FROM carabo.document WHERE business_code='LOAD_A' AND status='ACCEPTED' AND issue_date BETWEEN '2026-01-01' AND '2026-01-31' GROUP BY doc_type",
            'producer_aggregate': "SELECT p.id,COUNT(d.id),MAX(d.created_at) FROM carabo.producer_integration p LEFT JOIN carabo.document d ON d.producer_id=p.id AND d.business_code=p.business_code WHERE p.business_code='LOAD_A' GROUP BY p.id ORDER BY p.status,p.name"
        }
        self.result['plans'] = {key:json.loads(self.sql('EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) '+query)) for key,query in statements.items()}
        # Candidate index is created only in the disposable database, then removed.
        self.sql('CREATE INDEX bench_document_feed ON carabo.document (business_code,created_at DESC); ANALYZE carabo.document;')
        self.result['plans']['documents_page_candidate_index'] = json.loads(self.sql('EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) '+statements['documents_page']))
        self.sql('DROP INDEX carabo.bench_document_feed;')

    def save(self):
        (self.output / 'results.json').write_text(json.dumps(self.result,indent=2))

    def cleanup(self):
        if self.app: run('docker','stop','-t','10',self.app,check=False)
        for name in reversed(self.containers): run('docker','rm','-f',name,check=False)
        if self.network: run('docker','network','rm',self.network,check=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--rounds',type=int,default=2,choices=[1,2])
    args = parser.parse_args()
    args.output = args.output.resolve(); args.output.mkdir(parents=True,exist_ok=False)
    benchmark = Benchmark(args.output,args.rounds)
    try:
        with tempfile.TemporaryDirectory(prefix=benchmark.prefix) as temporary:
            benchmark.setup(Path(temporary))
            # Alternate order to expose warming/background-load effects.
            for number in range(1,args.rounds+1):
                for version in (('baseline','current') if number % 2 else ('current','baseline')):
                    benchmark.execute_round(version,number)
            benchmark.explain()
        benchmark.result['host_after'] = host_guard()
        benchmark.result['finished_utc'] = dt.datetime.now(dt.timezone.utc).isoformat()
        benchmark.result['completed'] = True
    except BaseException as error:
        benchmark.result['failure'] = str(error)
        raise
    finally:
        benchmark.save(); benchmark.cleanup()


if __name__ == '__main__': main()
