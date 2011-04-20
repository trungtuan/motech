package org.motechproject.server.service;


import org.motechproject.outbox.dao.OutboundVoiceMessageDao;
import org.motechproject.outbox.model.OutboundVoiceMessage;
import org.motechproject.outbox.model.OutboundVoiceMessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 *
 */
public class VoiceOutboxServiceImpl implements VoiceOutboxService {

    final Logger log = LoggerFactory.getLogger(VoiceOutboxServiceImpl.class);

    @Autowired
    OutboundVoiceMessageDao outboundVoiceMessageDao;


    @Override
    public void addMessage(OutboundVoiceMessage outboundVoiceMessage) {

        log.info("Add message: " + outboundVoiceMessage);

        if (outboundVoiceMessage == null) {
            throw new IllegalArgumentException("OutboundVoiceMessage can not be null.");
        }
        outboundVoiceMessageDao.add(outboundVoiceMessage);
    }

    @Override
    public OutboundVoiceMessage getNextPendingMessage(String partyId) {

        log.info("Get next pending message for the party ID: " + partyId);

        if (partyId == null || partyId.isEmpty()) {
            throw new IllegalArgumentException("Party ID can not be null or empty.");
        }

        OutboundVoiceMessage nextPendingVoiceMessage = null;
        List<OutboundVoiceMessage> pendingVoiceMessages = outboundVoiceMessageDao.getPendingMessages(partyId);

        if (pendingVoiceMessages.size() > 0) {

            nextPendingVoiceMessage = pendingVoiceMessages.get(0);
        }

        return nextPendingVoiceMessage;
    }

    @Override
    public OutboundVoiceMessage getMessageById(String outboundVoiceMessageId) {

         log.info("Get message by ID: " + outboundVoiceMessageId);

        if (outboundVoiceMessageId == null || outboundVoiceMessageId.isEmpty()) {
            throw new IllegalArgumentException("outboundVoiceMessageId can not be null or empty.");
        }
        return outboundVoiceMessageDao.get(outboundVoiceMessageId);
    }

    @Override
    public void removeMessage(String outboundVoiceMessageId) {

        log.info("Remove message ID: " + outboundVoiceMessageId);

        if (outboundVoiceMessageId == null || outboundVoiceMessageId.isEmpty()) {
            throw new IllegalArgumentException("outboundVoiceMessageId can not be null or empty.");
        }
        OutboundVoiceMessage outboundVoiceMessage = getMessageById(outboundVoiceMessageId);

        outboundVoiceMessageDao.remove(outboundVoiceMessage);
    }

    @Override
    public void setMessageStatus(String outboundVoiceMessageId, OutboundVoiceMessageStatus status) {

        log.info("Set status: "+ status +" to the message ID: " + outboundVoiceMessageId);

         if (outboundVoiceMessageId == null || outboundVoiceMessageId.isEmpty()) {
            throw new IllegalArgumentException("outboundVoiceMessageId can not be null or empty.");
        }

        OutboundVoiceMessage outboundVoiceMessage = getMessageById(outboundVoiceMessageId);
        outboundVoiceMessage.setStatus(status);

        outboundVoiceMessageDao.update(outboundVoiceMessage);
    }

    @Override
    public int getNumberPendingMessages(String partyId) {

        log.info("Get number of pending messages for the party ID: " + partyId);

        if (partyId == null || partyId.isEmpty()) {
            throw new IllegalArgumentException("Party ID can not be null or empty.");
        }

        return outboundVoiceMessageDao.getPendingMessages(partyId).size();
    }
}
