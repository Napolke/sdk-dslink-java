package org.dsa.iot.dslink.util;

/**
 * Global string references for the system properties in use.
 *
 * @author Samuel Grenier
 * @see System#getProperty(String)
 */
public class PropertyReference {

    public static final String NAMESPACE = "dslink";

    /**
     * A boolean property that determines the sdk should perform any
     * validations. Currently only the dslink.json is validated.
     *
     * Default value is true.
     */
    public static final String VALIDATE = NAMESPACE + ".validate";

    /**
     * A boolean property that determines whether to validate the
     * dslink.json configuration fields.
     *
     * Default value is true.
     */
    public static final String VALIDATE_JSON = VALIDATE + ".json";

    /**
     * A boolean property that determines whether to validate the handler
     * class or not. The field must still exist at a minimum.
     *
     * Default value is true.
     */
    public static final String VALIDATE_HANDLER = VALIDATE + ".handler_class";
}