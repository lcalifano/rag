package com.documents.userservice.exceptions;

public class ProviderAlreadyExistsException extends RuntimeException {
    public ProviderAlreadyExistsException(String provider) {
        super("Provider già esistente: " + provider);
    }
}
