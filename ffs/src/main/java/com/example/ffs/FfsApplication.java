package com.example.ffs;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.assertj.core.groups.Tuple;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@SpringBootApplication
public class FfsApplication {

	public static void main(String[] args) {
		SpringApplication.run(FfsApplication.class, args);
	}
	
	@Component
	public class RounteHandler {
		private FluxFlexService fluxFlexService;

		public RounteHandler(FluxFlexService fluxFlexService) {
			this.fluxFlexService = fluxFlexService;
		}
		public Mono<ServerResponse> all(ServerRequest serverRequest) {
			return ServerResponse.ok().body(fluxFlexService.all(),Movie.class);
		}
		
		public Mono<ServerResponse> events(ServerRequest serverRequest) {
			String movieId = serverRequest.pathVariable("movieId");
			return ServerResponse.ok()
					.contentType(MediaType.TEXT_EVENT_STREAM)
					.body(fluxFlexService.streamOfStreams(movieId),MovieEvent.class);
		}
		public Mono<ServerResponse> byId(ServerRequest serverRequest) {
			String movieId = serverRequest.pathVariable("movieId");
			return ServerResponse.ok()
					.body(fluxFlexService.findById(movieId),Movie.class);
		}
		
	}
	
	@Bean
	RouterFunction<?> routes(RounteHandler rounteHandler){
		return RouterFunctions.route(RequestPredicates.GET("/movies"), rounteHandler::all)
				.andRoute(RequestPredicates.GET("/movies/{movieId}"), rounteHandler::byId)
				.andRoute(RequestPredicates.GET("/movies/{movieId}/events"), rounteHandler::events);
	}
	
	@Bean
	CommandLineRunner movies(MovieRepository movieRepository) {
	
		return ags-> {
			movieRepository.deleteAll().subscribe(null, null, ()->{
				Stream.of("Silence of Lmadas","Aeon flux","Lord of rinds","bahubalamdas","T U Mono")
				.forEach(title->movieRepository.save(new Movie(UUID.randomUUID().toString(), title))
						.subscribe(System.out::println));
			});
		};
	}
}
//@RestController
class FluxFlexController {
	private FluxFlexService fluxFlexService;

	public FluxFlexController(FluxFlexService fluxFlexService) {
		this.fluxFlexService = fluxFlexService;
	}
	@GetMapping("/movies")
	public Flux<Movie> all() {
		return fluxFlexService.all();
	}

	@GetMapping("/movies/{movieId}")
	public Mono<Movie> findById(@PathVariable String movieId) {
		return fluxFlexService.findById(movieId);
	}

	@GetMapping(produces=MediaType.TEXT_EVENT_STREAM_VALUE, value="/movies/{movieId}/events")
	public Flux<MovieEvent> crossTheStreams(@PathVariable String movieId) {
		return fluxFlexService.streamOfStreams(movieId);
	}
}

@Service
class FluxFlexService {
	private MovieRepository movieRepository;
	public FluxFlexService(MovieRepository movieRepository) {
		this.movieRepository = movieRepository;
	}
	public Mono<Movie> findById(String id) {
		return movieRepository.findById(id);
	}
	public Flux<MovieEvent> streamOfStreams(String id) {
		return findById(id).flatMapMany(movie-> {
			Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));
			Flux<MovieEvent> events = Flux.fromStream(Stream.generate(()->new MovieEvent(movie, new Date())));
			return Flux.zip(interval, events).map(Tuple2::getT2);
		});
	}
	public Flux<Movie> all() {
		return movieRepository.findAll();
	}
}

interface MovieRepository extends ReactiveMongoRepository<Movie, String> {
	
}

@NoArgsConstructor
@AllArgsConstructor
@Data
@Document
class Movie {
	@Id
	private String id;
	private String title;

}

@NoArgsConstructor
@AllArgsConstructor
@Data
class MovieEvent {
	Movie movie;
	Date now;
}

