package io.carabo.application.producer.ports;

public interface ProducerKeys {
    String generate();
    String hash(String rawKey);
}
