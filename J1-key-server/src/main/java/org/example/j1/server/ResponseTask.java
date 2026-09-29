package org.example.j1.server;

import lombok.Value;

@Value
public class ResponseTask {
    ConnectionContext connection;
    IssuedCredential credential;
    String errorMessage;

    public static ResponseTask success(ConnectionContext connection, IssuedCredential credential) {
        return new ResponseTask(connection, credential, null);
    }

    public static ResponseTask error(ConnectionContext connection, String errorMessage) {
        return new ResponseTask(connection, null, errorMessage);
    }

    public boolean isSuccess() {
        return credential != null;
    }
}
