package com.documents.userservice.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class ModelProviderValidatorClass implements ConstraintValidator<ModelProviderValidator, String> {
    private Set<String> validValues;

    @Override
    public void initialize(ModelProviderValidator constraintAnnotation) {
        validValues = Arrays.stream(ModelProvider.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        if (s == null) return false;
        return validValues.contains(s.toUpperCase());
    }
}
