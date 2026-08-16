package com.taut0logy.jmeet.mail;

import java.util.Map;

public record EmailMessage(String to, String subject, String template, Map<String, Object> model) {
}
