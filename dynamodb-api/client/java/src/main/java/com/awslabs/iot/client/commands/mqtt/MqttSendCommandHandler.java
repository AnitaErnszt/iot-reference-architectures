package com.awslabs.iot.client.commands.mqtt;

import com.awslabs.general.helpers.interfaces.IoHelper;
import com.awslabs.general.helpers.interfaces.JsonHelper;
import com.awslabs.iot.client.commands.SharedCommunication;
import com.awslabs.iot.client.commands.SharedMqtt;
import com.awslabs.iot.client.commands.interfaces.CommandHandler;
import com.awslabs.iot.client.data.ImmutableSendRequest;
import com.awslabs.iot.client.data.SendRequest;
import com.awslabs.iot.client.parameters.interfaces.ParameterExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

public class MqttSendCommandHandler implements CommandHandler {
    private final Logger log = LoggerFactory.getLogger(MqttSendCommandHandler.class);
    private static final String SEND = SharedCommunication.SEND;
    @Inject
    ParameterExtractor parameterExtractor;
    @Inject
    IoHelper ioHelper;
    @Inject
    JsonHelper jsonHelper;
    @Inject
    SharedMqtt sharedMqtt;

    @Inject
    public MqttSendCommandHandler() {
    }

    @Override
    public int requiredParameters() {
        return 2;
    }

    @Override
    public void innerHandle(String input) {
        List<String> parameters = parameterExtractor.getParameters(input);

        String recipientUuid = parameters.get(0);
        String message = parameters.get(1);

        SendRequest sendRequest = ImmutableSendRequest.builder()
                .clientMessageId(1111)
                .imei(recipientUuid)
                .message(message)
                .build();

        log.info("SendResponse: " + sharedMqtt.sendMessage(recipientUuid, sendRequest.toJson()));
    }

    @Override
    public String getCommandString() {
        return SEND;
    }

    @Override
    public boolean supportsSerial() {
        return false;
    }

    @Override
    public boolean supportsMqtt() {
        return true;
    }

    @Override
    public String getHelp() {
        return "Sends a message.";
    }

    public ParameterExtractor getParameterExtractor() {
        return this.parameterExtractor;
    }

    public IoHelper getIoHelper() {
        return this.ioHelper;
    }
}
