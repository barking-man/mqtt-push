package com.mark.exception;

/**
 * @author mk
 * @data 2025/9/18 23:52
 * @description
 */
public class ServiceException extends RuntimeException{
    public ServiceException(String message) {
        super(message);
    }
}
