package eu.gpefrance.tenders.company;

import java.time.Clock;

import org.springframework.stereotype.Component;

/**
 * What every list of the company needs, given once to each of them.
 */
@Component
public record CompanyContext(CompanyFiles files, CompanyProperties properties, Clock clock) {
}
