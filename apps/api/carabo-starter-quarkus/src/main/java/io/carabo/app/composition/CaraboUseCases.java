package io.carabo.app.composition;

import io.carabo.application.reporting.ReportUseCases;
import io.carabo.application.reporting.ports.ReportReader;
import io.carabo.application.reporting.ports.SalesWorkbook;
import io.carabo.application.producer.ProducerUseCases;
import io.carabo.application.producer.ports.ProducerDirectory;
import io.carabo.application.producer.ports.ProducerKeys;
import io.carabo.application.producer.ports.ProducerUnitOfWork;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import java.time.Clock;

@ApplicationScoped
public class CaraboUseCases {
    @Produces
    @Singleton
    ReportUseCases reports(ReportReader reader, SalesWorkbook workbook) {
        return new ReportUseCases(reader, workbook);
    }

    @Produces
    @Singleton
    ProducerUseCases producers(ProducerDirectory directory, ProducerUnitOfWork work, ProducerKeys keys) {
        return new ProducerUseCases(directory, work, keys, Clock.systemUTC());
    }
}
