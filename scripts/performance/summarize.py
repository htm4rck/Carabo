#!/usr/bin/env python3
"""Summarize the raw observations from minimal_load.py; no averaging of percentiles."""
import collections
import json
import math
from pathlib import Path
import statistics
import sys


def aggregate(result):
    groups = collections.defaultdict(list)
    for sample in result['samples']:
        groups[(sample['scenario'], sample['clients'], sample['version'])].append(sample)
    output = {}
    for key, samples in groups.items():
        latencies = sorted(value for sample in samples for value in sample['latency_ms'])
        requests = sum(sample['requests'] for sample in samples)
        output[key] = {
            'requests': requests,
            'errors': sum(len(sample['errors']) for sample in samples),
            'p50_ms': statistics.median(latencies),
            'p95_ms': latencies[math.ceil(len(latencies) * .95) - 1],
            'rss_peak_mib': max(sample['rss_peak_mib'] for sample in samples),
            'sql_calls_per_request': sum(query['calls'] for sample in samples for query in sample['sql']) / requests,
            'sql_ms_per_request': sum(query['total_ms'] for sample in samples for query in sample['sql']) / requests,
            'cpu_interval_ms_per_request': sum(sample['cpu_ms'] for sample in samples) / requests,
        }
    return output


def main():
    result = json.loads(Path(sys.argv[1]).read_text())
    groups = aggregate(result)
    print('| Escenario | Clientes | N por versión | Mediana anterior → actual | P95 anterior → actual | Cambio mediana | SQL/petición anterior → actual |')
    print('|---|---:|---:|---:|---:|---:|---:|')
    for scenario, clients, version in groups:
        if version != 'baseline': continue
        previous = groups[(scenario,clients,'baseline')]
        current = groups[(scenario,clients,'current')]
        change = (current['p50_ms']/previous['p50_ms']-1)*100
        print(f"| {scenario} | {clients} | {previous['requests']} / {current['requests']} | "
              f"{previous['p50_ms']:.1f} → {current['p50_ms']:.1f} ms | {previous['p95_ms']:.1f} → {current['p95_ms']:.1f} ms | "
              f"{change:+.1f}% | {previous['sql_calls_per_request']:.0f} → {current['sql_calls_per_request']:.0f} |")
    print('\nP95 calculado sobre las latencias originales agrupadas de ambas rondas. Excel tiene pocas observaciones.')
    for version in ('baseline','current'):
        samples = [sample for sample in result['samples'] if sample['version'] == version]
        print(f"{version}: {sum(sample['requests'] for sample in samples)} peticiones medidas, "
              f"{sum(len(sample['errors']) for sample in samples)} errores, "
              f"pico RSS {max(sample['rss_peak_mib'] for sample in samples):.1f} MiB")


if __name__ == '__main__': main()
