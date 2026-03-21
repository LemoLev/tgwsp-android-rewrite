import backend
import asyncio
from java import static_proxy, jarray, jint, method, jvoid
from java.lang import String

class ProxyControl(static_proxy()):
    @method(jvoid, [String, jint, String])
    def start_proxy(self, p_host: str, p_port: int, p_dcip: str):
        cmd = ["--host", p_host, "--port", str(p_port)]
        dcips = p_dcip.split("\n")
        for d in dcips:
            cmd.append("--dc-ip")
            cmd.append(d)
        print(cmd)
        backend.main(cmd)

    @method(jvoid, [])
    def stop_proxy(self):
        backend.STOP_EVENT.set()
        print("stop event set")
        backend.STOP_EVENT.clear()