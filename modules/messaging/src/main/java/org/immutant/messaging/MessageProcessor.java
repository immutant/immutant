package org.immutant.messaging;

import javax.jms.Message;
import org.projectodd.polyglot.messaging.BaseMessageProcessor;

public class MessageProcessor extends BaseMessageProcessor {

    @Override
    public void onMessage(Message message) {
        MessageProcessorGroup group = (MessageProcessorGroup) getGroup();
        // TODO: pass the real function to a wrapper to handle tx, etc
        group.getFunction().run();
    }
    
 }
