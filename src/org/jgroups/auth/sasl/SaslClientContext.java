package org.jgroups.auth.sasl;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.protocols.SASL;
import org.jgroups.protocols.SaslHeader;
import org.jgroups.protocols.SaslHeader.Type;

public class SaslClientContext implements SaslContext {
    private static final byte[] EMPTY_CHALLENGE = new byte[0];
    SaslClient client;

    public SaslClientContext(final String mech, final String server_name, final CallbackHandler callback_handler, final Map<String, String> props, final Subject subject) throws SaslException {
        if (subject != null) {
            try {
                client = Subject.doAs(subject, new PrivilegedExceptionAction<SaslClient>() {

                    @Override
                    public SaslClient run() throws Exception {
                        return Sasl.createSaslClient(new String[] { mech }, null, "jgroups", server_name, props, callback_handler);
                    }
                });
            } catch (PrivilegedActionException e) {
                throw (SaslException)e.getCause(); // The createSaslServer will only throw this type of exception
            }
        } else {
            client = Sasl.createSaslClient(new String[] { mech }, null, "jgroups", server_name, props, callback_handler);
        }
    }

    @Override
    public boolean isSuccessful() {
        return client.isComplete();
    }

    @Override
    public boolean needsWrapping() {
        if (client.isComplete()) {
            String qop = (String) client.getNegotiatedProperty(Sasl.QOP);
            return (qop != null && (qop.equalsIgnoreCase("auth-int") || qop.equalsIgnoreCase("auth-conf")));
        } else {
            return false;
        }
    }

    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
        return client.wrap(outgoing, offset, len);
    }

    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
        return client.unwrap(incoming, offset, len);
    }

    @Override
    public Message nextMessage(Address address, SaslHeader header) throws SaslException {
        Message message = new Message(address).setFlag(Message.Flag.OOB);
        return addHeader(message, header.getPayload());
    }

    @Override
    public void dispose() {
        try {
            client.dispose();
        } catch (SaslException e) {
        }
    }

    public Message addHeader(Message msg, byte[] payload) throws SaslException {
        byte[] response;
        if (payload == null) {
            if (client.hasInitialResponse()) {
                response = client.evaluateChallenge(EMPTY_CHALLENGE);
            } else {
                response = EMPTY_CHALLENGE;
            }
        } else {
            response = client.evaluateChallenge(payload);
        }
        if (response != null) {
            return msg.putHeader(SASL.SASL_ID, new SaslHeader(Type.RESPONSE, response));
        } else {
            return null;
        }
    }
}