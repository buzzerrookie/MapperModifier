package com.suntao.exceptions;

public class ModifyException extends Exception {
    private static final long serialVersionUID = 4931836759437158436L;

    public ModifyException() {
        super();
    }
    
    public ModifyException(String message) {
        super(message);
    }
    
    public ModifyException(String message, Throwable cause) {
        super(message, cause);
    }
}
