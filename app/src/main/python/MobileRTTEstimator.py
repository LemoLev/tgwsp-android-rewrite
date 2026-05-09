import asyncio
import time
from typing import Optional


class MobileRTTEstimator:
    __slots__ = ('alpha', 'beta', 'srtt', 'rttvar', 'min_rtt', '_last_update', '_current_rto',
                 '_max_rto')

    def __init__(self, alpha: float = 0.125, beta: float = 0.25):
        self.alpha = alpha
        self.beta = beta

        self.srtt: Optional[float] = None
        self.rttvar: float = 0.0
        self.min_rtt: float = float('inf')
        self._last_update: float = 0.0
        self._current_rto = 5.0  # Дефолт сек
        self._max_rto = 10000.0

    def update(self, measured_rtt_ms: float):
        """Обновляет метрики. Принимает RTT в миллисекундах."""
        now = time.perf_counter()

        # Если связи не было больше 30 секунд, сбрасываем историю.
        # В мобилках за это время может быть переезд в другой район или на другую вышку.
        if now - self._last_update > 30.0:
            self.srtt = None

        if self.srtt is None:
            # Первая инициализация по RFC 6298
            self.srtt = measured_rtt_ms
            self.rttvar = measured_rtt_ms / 2
        else:
            # Считаем джиттер (вариацию)
            delta = abs(self.srtt - measured_rtt_ms)
            self.rttvar = (1 - self.beta) * self.rttvar + self.beta * delta
            # Считаем сглаженное среднее
            self.srtt = (1 - self.alpha) * self.srtt + self.alpha * measured_rtt_ms

        # Обновляем абсолютный минимум для оценки пропускной способности
        if measured_rtt_ms < self.min_rtt:
            self.min_rtt = measured_rtt_ms

        self._last_update = now
        self._current_rto = min(35000, (self.srtt + max(50.0,
                                                        4 * self.rttvar))) / 1000  # возврат в секундах

    def penalty_timeout(self):
        """Вызывать, если словили asyncio.TimeoutError"""
        if self.srtt is not None:
            # Экспоненциальное увеличение (как в TCP)
            self.srtt *= 2.0
            # Раздуваем вариацию, чтобы RTO вырос еще сильнее
            self.rttvar *= 1.5
            # Ограничиваем сверху, чтобы не уйти в бесконечность
            self.srtt = min(self.srtt, self._max_rto)
            self._current_rto = min(35000, (self.srtt + max(50.0,
                                                            4 * self.rttvar))) / 1000  # возврат в секундах
