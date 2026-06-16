#!/usr/bin/env python3
"""
巢车传感器模拟器
每1分钟通过HTTP POST向后端上报模拟传感器数据：
- 悬臂应力 (boom_stress)
- 吊篮晃动 (basket_sway)
- 高度 (height)
- 观察距离 (observation_distance)
- 风速 (wind_speed)
- 风向 (wind_direction)
- 温度 (temperature)

使用方法:
  python simulator.py [--api-url URL] [--cart-id ID] [--interval SECONDS]

示例:
  python simulator.py --api-url http://localhost:8080 --cart-id a1b2c3d4-e5f6-7890-abcd-ef1234567890 --interval 60
"""

import argparse
import json
import math
import random
import sys
import time
from datetime import datetime

try:
    import requests
except ImportError:
    print("请先安装 requests 库: pip install requests")
    sys.exit(1)


class NestCartSimulator:

    def __init__(self, api_url, cart_id, interval=60):
        self.api_url = api_url.rstrip('/')
        self.cart_id = cart_id
        self.interval = interval
        self.tick = 0
        self.base_height = 10.0
        self.current_height = self.base_height
        self.base_wind_speed = 3.0
        self.stress_limit = 8.0e6
        self.sway_limit = 0.5
        self.height_target = self.base_height
        self.wind_phase = 0.0

    def generate_sensor_data(self):
        self.tick += 1
        self.wind_phase += 0.1

        wind_speed = self.base_wind_speed + 5.0 * math.sin(self.wind_phase) + random.gauss(0, 1.5)
        wind_speed = max(0, wind_speed)

        wind_direction = (45 + 30 * math.sin(self.wind_phase * 0.3) + random.gauss(0, 10)) % 360

        if self.tick % 10 == 0:
            self.height_target = random.uniform(6, 15)
        height_diff = self.height_target - self.current_height
        self.current_height += height_diff * 0.1 + random.gauss(0, 0.05)
        self.current_height = max(4, min(18, self.current_height))

        gravity_stress = 150.0 * 9.81 * 8.0 * math.sqrt(0.01 / math.pi) / 8.33e-6
        wind_force_per_length = 0.5 * 1.225 * wind_speed ** 2 * 1.2 * math.sqrt(0.01) * 2
        wind_moment = wind_force_per_length * 8.0 ** 2 / 2
        wind_stress = wind_moment * math.sqrt(0.01 / math.pi) / 8.33e-6
        total_stress = gravity_stress + wind_stress
        boom_stress = total_stress * (1 + random.gauss(0, 0.05))
        boom_stress = max(0, boom_stress)

        gravity_deflection = (150.0 * 9.81 * 8.0 ** 3) / (3 * 1.2e10 * 8.33e-6)
        wind_deflection = (wind_force_per_length * 8.0 ** 4) / (8 * 1.2e10 * 8.33e-6)
        basket_sway = (gravity_deflection + wind_deflection) * (1 + random.gauss(0, 0.1))
        basket_sway = max(0, basket_sway)

        horizon_dist = math.sqrt(2 * 6371000 * self.current_height)
        observation_distance = horizon_dist * random.uniform(0.6, 0.95)

        temperature = 20 + 5 * math.sin(self.wind_phase * 0.01) + random.gauss(0, 2)

        data = {
            'cartId': self.cart_id,
            'boomStress': round(boom_stress, 2),
            'basketSway': round(basket_sway, 4),
            'height': round(self.current_height, 2),
            'observationDistance': round(observation_distance, 1),
            'windSpeed': round(wind_speed, 2),
            'windDirection': round(wind_direction, 1),
            'temperature': round(temperature, 1)
        }

        return data

    def send_data(self, data):
        url = f'{self.api_url}/api/carts/{self.cart_id}/sensor-data'
        try:
            resp = requests.post(url, json=data, timeout=10)
            if resp.status_code == 200:
                return True
            else:
                print(f'  [ERROR] HTTP {resp.status_code}: {resp.text}')
                return False
        except requests.exceptions.ConnectionError:
            print(f'  [ERROR] 无法连接到 {self.api_url}')
            return False
        except Exception as e:
            print(f'  [ERROR] {e}')
            return False

    def run(self):
        print('=' * 60)
        print('  巢车传感器模拟器')
        print(f'  巢车ID: {self.cart_id}')
        print(f'  API地址: {self.api_url}')
        print(f'  上报间隔: {self.interval}秒')
        print('=' * 60)
        print()

        while True:
            data = self.generate_sensor_data()
            timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

            stress_ratio = data['boomStress'] / self.stress_limit
            sway_ratio = data['basketSway'] / self.sway_limit

            status = '正常'
            if stress_ratio > 0.95 or sway_ratio > 0.9:
                status = '危险'
            elif stress_ratio > 0.8 or sway_ratio > 0.7:
                status = '警告'

            print(f'[{timestamp}] Tick #{self.tick} [{status}]')
            print(f'  应力: {data["boomStress"]/1e6:.2f} MPa ({stress_ratio*100:.1f}%)')
            print(f'  晃动: {data["basketSway"]*1000:.1f} mm ({sway_ratio*100:.1f}%)')
            print(f'  高度: {data["height"]:.1f} m')
            print(f'  观察距离: {data["observationDistance"]:.0f} m')
            print(f'  风速: {data["windSpeed"]:.1f} m/s  风向: {data["windDirection"]:.0f}°')
            print(f'  温度: {data["temperature"]:.1f} °C')

            success = self.send_data(data)
            if success:
                print(f'  [OK] 数据上报成功')
            else:
                print(f'  [FAIL] 数据上报失败')

            print()
            time.sleep(self.interval)


def main():
    parser = argparse.ArgumentParser(description='巢车传感器模拟器')
    parser.add_argument('--api-url', default='http://localhost:8080',
                        help='后端API地址 (默认: http://localhost:8080)')
    parser.add_argument('--cart-id', default='a1b2c3d4-e5f6-7890-abcd-ef1234567890',
                        help='巢车ID (默认: 巢车一号)')
    parser.add_argument('--interval', type=int, default=60,
                        help='上报间隔秒数 (默认: 60)')
    parser.add_argument('--wind-base', type=float, default=3.0,
                        help='基础风速 m/s (默认: 3.0)')
    parser.add_argument('--height-base', type=float, default=10.0,
                        help='基础高度 m (默认: 10.0)')

    args = parser.parse_args()

    sim = NestCartSimulator(
        api_url=args.api_url,
        cart_id=args.cart_id,
        interval=args.interval
    )
    sim.base_wind_speed = args.wind_base
    sim.base_height = args.height_base
    sim.current_height = args.height_base
    sim.height_target = args.height_base

    try:
        sim.run()
    except KeyboardInterrupt:
        print('\n模拟器已停止')


if __name__ == '__main__':
    main()
