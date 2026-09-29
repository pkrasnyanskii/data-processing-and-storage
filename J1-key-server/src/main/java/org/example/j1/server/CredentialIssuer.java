package org.example.j1.server;

public interface CredentialIssuer {
    IssuedCredential issue(String subjectName) throws Exception;
}
