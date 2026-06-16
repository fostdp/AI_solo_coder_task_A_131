package com.nestcart.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nestcart.dto.SensorDataRequest;
import com.nestcart.dtu.DtuSensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.broker.username:}")
    private String username;

    @Value("${mqtt.broker.password:}")
    private String password;

    @Value("${mqtt.broker.client-id}")
    private String clientId;

    @Value("${mqtt.broker.topic.sensor-data}")
    private String sensorDataTopic;

    @Value("${mqtt.broker.topic.alert}")
    private String alertTopic;

    private final DtuSensorService dtuSensorService;
    private final ObjectMapper objectMapper;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        if (username != null && !username.isBlank()) {
            options.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            options.setPassword(password.toCharArray());
        }
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mqttAlertOutboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId + "-in", mqttClientFactory(), sensorDataTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        log.info("MQTT 消费者已初始化, 订阅主题: {}", sensorDataTopic);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler mqttInputHandler() {
        return message -> {
            try {
                String payload = message.getPayload().toString();
                String topic = (String) message.getHeaders().get("mqtt_receivedTopic");

                log.debug("收到 MQTT 消息, 主题: {}, 内容: {}", topic, payload);

                SensorDataRequest request = objectMapper.readValue(payload, SensorDataRequest.class);

                String cartIdStr = extractCartIdFromTopic(topic, request.getCartId());
                if (cartIdStr == null) {
                    log.warn("无法确定 cartId, 丢弃消息: {}", payload);
                    return;
                }

                UUID cartId = UUID.fromString(cartIdStr);
                dtuSensorService.receiveAndValidate(cartId, request);

            } catch (Exception e) {
                log.error("处理 MQTT 传感器数据失败: {}", e.getMessage(), e);
            }
        };
    }

    private String extractCartIdFromTopic(String topic, UUID fallback) {
        if (fallback != null) {
            return fallback.toString();
        }
        if (topic != null) {
            String[] parts = topic.split("/");
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        return null;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttAlertOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(clientId + "-out", mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultTopic(alertTopic);
        handler.setDefaultQos(1);
        return handler;
    }
}
