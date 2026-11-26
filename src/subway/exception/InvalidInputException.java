package subway.exception;

/** 站名、距离参数等用户输入不合法时抛出。 */
public class InvalidInputException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidInputException(String message) {
        super(message);
    }
}