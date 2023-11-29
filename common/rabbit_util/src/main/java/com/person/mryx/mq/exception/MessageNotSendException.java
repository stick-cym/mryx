package com.person.mryx.mq.exception;

public class MessageNotSendException extends RuntimeException{
    public MessageNotSendException(String message){
        super(message);
    }
}
