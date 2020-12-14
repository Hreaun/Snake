package view;

public class InvalidSettingsException extends Exception{
    public InvalidSettingsException(String errorMessage) {
        super(errorMessage);
    }
}
