package com.agrilink.notification;

/**
 * Outbound SMS. The default implementation only logs; register a bean of this type (e.g. for
 * AfroMessage or Ethio Telecom) and the logging fallback backs off.
 */
public interface SmsGateway {

    void send(String toE164, String message);
}
