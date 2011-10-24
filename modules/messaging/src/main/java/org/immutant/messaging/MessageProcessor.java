package org.immutant.messaging;

import javax.jms.Message;
import org.projectodd.polyglot.messaging.BaseMessageProcessor;

public class MessageProcessor extends BaseMessageProcessor {

    @Override
    public void onMessage(Message message) {
        MessageProcessorGroup group = (MessageProcessorGroup) getGroup();
        group.getRuntime().invoke("immutant.messaging/handle", group.getFunction(), message);
    }
    
 }
