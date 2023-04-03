package io.reflectoring.resilience4j.ratelimit;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.reflectoring.resilience4j.ratelimit.model.Flight;
import io.reflectoring.resilience4j.ratelimit.model.SearchRequest;
import io.reflectoring.resilience4j.ratelimit.services.FlightSearchService;
import io.vavr.CheckedFunction0;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

public class Examples
{

    CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
            .slowCallDurationThreshold(Duration.ofMillis(100))
            .build();
    CircuitBreaker circuitBreaker = CircuitBreaker.of("myCircuitBreaker", circuitBreakerConfig);


    String result = circuitBreaker.executeSupplier(() -> {
        URL url = null;
        try {
            url = new URL("https://example.com");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            con.setRequestMethod("GET");
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        }
        try {
            int status = con.getResponseCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String inputLine;
        StringBuffer content = new StringBuffer();
        while (true) {
            try {
                if (!((inputLine = in.readLine()) != null)) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            content.append(inputLine);
        }
        try {
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        con.disconnect();
        return content.toString();
    });

    void displayDefaultValues() {
        RateLimiterConfig config = RateLimiterConfig.ofDefaults();

        System.out.println("Limit for period = " + config.getLimitForPeriod());
        System.out.println(("Refresh period = " + Duration.from(config.getLimitRefreshPeriod()).toNanos()));
        System.out.println("Timeout value = " + Duration.from(config.getTimeoutDuration()).toMillis());
    }

    void basicExample() {
        RateLimiterConfig config = RateLimiterConfig.custom().
            limitForPeriod(1).
            limitRefreshPeriod(Duration.ofSeconds(1)).
            timeoutDuration(Duration.ofSeconds(1)).build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter limiter = registry.rateLimiter("flightSearchService");

        FlightSearchService service = new FlightSearchService();
        SearchRequest request = new SearchRequest("NYC", "LAX", "07/31/2020");

        Supplier<List<Flight>> flightsSupplier = RateLimiter.decorateSupplier(limiter, () -> service.searchFlights(request));
        for (int i=0; i<3; i++) {
            System.out.println(flightsSupplier.get());
        }
    }

    void timeoutExample() {
        RateLimiterConfig config = RateLimiterConfig.custom().
            limitForPeriod(1).
            limitRefreshPeriod(Duration.ofSeconds(1)).
            timeoutDuration(Duration.ofMillis(250)).build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter limiter = registry.rateLimiter("flightSearchService");

        FlightSearchService service = new FlightSearchService();
        SearchRequest request = new SearchRequest("NYC", "LAX", "07/31/2020");

        Supplier<List<Flight>> flightsSupplier = RateLimiter.decorateSupplier(limiter, () -> service.searchFlights(request));
        for (int i=0; i<3; i++) {
            try {
                System.out.println(flightsSupplier.get());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void checkedExceptionExample() {
        RateLimiterConfig config = RateLimiterConfig.custom().
            limitForPeriod(1).
            limitRefreshPeriod(Duration.ofSeconds(1)).
            timeoutDuration(Duration.ofSeconds(1)).build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter limiter = registry.rateLimiter("flightSearchService");

        FlightSearchService service = new FlightSearchService();
        SearchRequest request = new SearchRequest("NYC", "LAX", "07/31/2020");

        CheckedFunction0<List<Flight>> flights = RateLimiter.decorateCheckedSupplier(limiter, () -> service.searchFlightsThrowingException(request));
        try {
            System.out.println(flights.apply());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static void main( String[] args ) {
        Examples examples = new Examples();
        System.out.println("---------------------------- displayDefaultValues -------------------------------------------");
        examples.displayDefaultValues();
        System.out.println("-----------------------------------------------------------------------");
        System.out.println("----------------------------- usagePattern ------------------------------------------");
        examples.basicExample();
        System.out.println("-----------------------------------------------------------------------");
        System.out.println("----------------------------- timeoutExample ------------------------------------------");
        examples.timeoutExample();
        System.out.println("-----------------------------------------------------------------------");
        System.out.println("------------------------------ checkedExceptionExample -----------------------------------------");
        examples.checkedExceptionExample();
        System.out.println("-----------------------------------------------------------------------");
        System.out.println("------------------------------ multipleLimits_2rps_40rpm_sequential -----------------------------------------");

    }
}