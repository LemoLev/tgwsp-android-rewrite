import backend
import asyncio
from java import static_proxy, jarray, jint, method, jvoid, jboolean
from java.lang import String

class ProxyControl(static_proxy()):
    exception = "nothing. all good"
    isRunning = False
    host, port, dcip = "", 0, []

    @method(jvoid, [String, jint, String])
    def start_proxy(self, p_host: str, p_port: int, p_dcip: str):
        self.host, self.port, self.dcip = p_host, p_port, p_dcip
        cmd = ["--host", p_host, "--port", str(p_port)]
        dcips = p_dcip.split("\n")
        for d in dcips:
            cmd.append("--dc-ip")
            cmd.append(d)
        print(cmd)
        try:
            self.exception = "nothing. all good"
            backend.main(cmd)
            self.isRunning = True
        except Exception as e:
            self.exception = str(e)
            self.isRunning = False
            print(self.exception)

    @method(jvoid, [])
    def stop_proxy(self):
        self.host, self.port, self.dcip = "", 0, []
        self.isRunning = False
        backend.STOP_EVENT.set()
        print("stop event set")
        backend.STOP_EVENT.clear()

    @method(String, [])
    def check_proxy(self):
        return self.exception

    @method(String, [String, jint, String])
    def start_and_check(self, p_host: str, p_port: int, p_dcip: str):
        self.start_proxy(p_host, p_port, p_dcip)
        if self.isRunning or self.exception == "":
            return "SUCCESS"
        else:
            print(self.exception)
            return self.exception


