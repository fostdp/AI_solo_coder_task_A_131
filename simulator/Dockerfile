FROM python:3.12-alpine

LABEL maintainer="nestcart-project"
LABEL description="NestCart Sensor Simulator - MQTT + HTTP"

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY simulator.py .

ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && apk del tzdata

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD pgrep -f simulator.py >/dev/null 2>&1 || exit 1

ENTRYPOINT ["python", "-u", "/app/simulator.py"]
