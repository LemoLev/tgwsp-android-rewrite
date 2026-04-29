import asyncio
from java.lang import String
from typing import Optional

import tg_ws_proxy
from java import static_proxy, jarray, jint, method, jvoid, jboolean


class ProxyControl(static_proxy()):
    def __init__(self):
        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.stop_event: Optional[asyncio.Event] = None

    @method(jvoid, [String, jint, String, String])
    def start_proxy(self, p_host: str, p_port: int, p_dcip: str, secret: str):
        cmd = ["--port", str(p_port), "--host", p_host, "--secret", secret]
        dcips = p_dcip.split("\n")

        cmd.append("--dc-ip")
        proxies = []
        for d in dcips:
            proxies.append(d)

        cmd.append(proxies)
        print(cmd)

        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.stop_event = asyncio.Event()

        try:
            print("Start ProxyControl")
            tg_ws_proxy.main(cmd, self.stop_event, self.loop)
        except Exception as e:
            print(f"Proxy error: {e}")
        finally:
            try:
                pending = asyncio.all_tasks(self.loop)
                for task in pending:
                    task.cancel()
                if pending:
                    self.loop.run_until_complete( asyncio.gather(*pending, return_exceptions=True))

                self.loop.run_until_complete(self.loop.shutdown_asyncgens())
                self.loop.run_until_complete(self.loop.shutdown_default_executor())
            except Exception as e:
                print(f"Force close loop due to: {e}")
            finally:
                self.loop.close()
                print("Close loops")
                import gc
                gc.collect()
                import os
                print("fd process")
                print(len(os.listdir(f'/proc/{os.getpid()}/fd')))
                asyncio.set_event_loop(None)
                self.loop = None
                self.stop_event = None

    @method(jvoid, [])
    def stop_proxy(self):
        if self.loop and self.stop_event:
            self.loop.call_soon_threadsafe(self.stop_event.set)
        print("Stop ProxyControl")

    def warn_with_traceback(self, message, category, filename, lineno, file=None, line=None):
        log = file if file else sys.stderr
        traceback.print_stack(file=log)
        print(f"{filename}:{lineno}: {category.__name__}: {message}", file=log)
