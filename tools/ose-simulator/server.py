from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import base64, json, re

DOCUMENTS = {}

class Handler(BaseHTTPRequestHandler):
    def json(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status); self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload))); self.end_headers(); self.wfile.write(payload)

    def authorized(self):
        return self.headers.get("Authorization") == "Bearer carabo-ose-simulator"

    def do_POST(self):
        if self.path != "/api/v1/documents": return self.json(404, {"message":"not found"})
        if not self.authorized(): return self.json(401, {"message":"invalid token"})
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))) or b"{}")
        name = body.get("fileName", "")
        if not name or not body.get("contentFile"): return self.json(422, {"message":"fileName and contentFile are required"})
        mode = self.headers.get("X-OSE-Simulator-Mode", "ACCEPTED").upper()
        if mode == "TIMEOUT": return self.json(503, {"message":"simulated outage", "retryable":True})
        external = "sim-" + str(abs(hash(name)))
        status = mode if mode in ("ACCEPTED","OBSERVED","REJECTED","PENDING") else "ACCEPTED"
        cdr = base64.b64encode(("<ApplicationResponse><ID>"+external+"</ID></ApplicationResponse>").encode()).decode()
        DOCUMENTS[external] = {"status":status,"cdrContent":cdr}
        self.json(202 if status == "PENDING" else 200, {"externalId":external,"status":status,
            "accepted":status in ("ACCEPTED","OBSERVED"),"observed":status=="OBSERVED",
            "retryable":False,"responseCode":"0" if status != "REJECTED" else "SIM-001",
            "responseMessage":"OSE simulator: "+status,"cdrContent":cdr if status != "PENDING" else None})

    def do_GET(self):
        match = re.fullmatch(r"/api/v1/documents/([^/]+)/(status|cdr)", self.path)
        if not match: return self.json(404, {"message":"not found"})
        if not self.authorized(): return self.json(401, {"message":"invalid token"})
        item = DOCUMENTS.get(match.group(1))
        if not item: return self.json(404, {"message":"unknown external id"})
        if match.group(2) == "cdr": return self.json(200, {"cdrContent":item["cdrContent"]})
        self.json(200, {"externalId":match.group(1), **item, "accepted":item["status"] in ("ACCEPTED","OBSERVED")})

    def log_message(self, fmt, *args): pass

ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
