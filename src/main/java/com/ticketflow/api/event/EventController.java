package com.ticketflow.api.event;

import com.ticketflow.api.event.dto.CreateEventRequest;
import com.ticketflow.api.event.dto.EventResponse;
import com.ticketflow.api.event.dto.EventSummaryResponse;
import com.ticketflow.api.event.dto.UpdateEventRequest;
import com.ticketflow.api.shared.dto.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * [API REST] Camada web. Responsabilidade ÚNICA: traduzir HTTP <-> chamada
 * de service. Se você encontrar um 'if' de regra de negócio aqui, está no
 * lugar errado.
 *
 * [SPRING MVC] @RestController + @RequestMapping definem o prefixo comum.
 * @Validated na classe habilita validação de @PathVariable e @RequestParam
 * (o @Valid do @RequestBody funciona sem ela).
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Validated
public class EventController {
    private final EventService eventService;

    /**
     * [API REST] GET /api/v1/events?page=0&size=20&sort=startsAt,asc
     *
     * [SPRING DATA WEB] Pageable é resolvido AUTOMATICAMENTE dos query params
     * page, size e sort. Isso vem de um argument resolver que o Boot
     * autoconfigura — você não escreve nada.
     *
     * @PageableDefault protege a API: sem ele, um cliente pode pedir
     * ?size=1000000 e derrubar a aplicação. (O Boot tem um teto global
     * configurável via spring.data.web.pageable.max-page-size.)
     *
     * Retorna 200 com lista VAZIA quando não há resultado — nunca 404.
     * 404 é para RECURSO inexistente; uma coleção vazia existe.
     */
    @GetMapping
    public PagedResponse<EventSummaryResponse> listEvents(
            @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return eventService.findAll(pageable);
    }

    /**
     * [API REST] GET com filtro por query param.
     * Regra: @PathVariable IDENTIFICA o recurso; @RequestParam FILTRA/ORDENA.
     * Cidade é filtro, então é query param.
     */
    @GetMapping("/search")
    public PagedResponse<EventSummaryResponse> searchByCity(
            @RequestParam @NotBlank(message = "City is required") String city,
            @PageableDefault(size = 20, sort = "startsAt")Pageable pageable){
        return eventService.findPublishedByCity(city, pageable);
    }

    /**
     * [API REST] GET /api/v1/events/{id}
     * Retorno direto do DTO (sem ResponseEntity) porque o status é sempre 200.
     * Se não existir, o service lança e o handler devolve 404.
     */
    @GetMapping("/{id}")
    public EventResponse getEvent(@PathVariable Long id){
        return eventService.findById(id);
    }

    /**
     * [API REST] POST = criação. Dois detalhes que definem uma API bem feita:
     *
     * 1. Status 201 Created (não 200)
     * 2. Header 'Location' com a URI do recurso criado — permite ao cliente
     *    (ou a um bot HATEOAS) navegar direto para ele.
     *
     * [BEAN VALIDATION] @Valid dispara a validação do DTO ANTES de o método
     * executar. Se falhar, o Spring lança MethodArgumentNotValidException e
     * nosso handler devolve 400 com a lista de campos. O service nunca é
     * chamado com dado inválido — isso é "fail fast" na borda.
     *
     * @RequestBody: o Jackson desserializa o JSON no record.
     */
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request,
                                                       UriComponentsBuilder uriBuilder){   // [SPRING MVC] injetado; conhece host/porta
        EventResponse created = eventService.create(request);

        URI location = uriBuilder
                .path("/api/v1/event/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);   // 201 + Location
    }

    /**
     * [API REST] PUT = substituição TOTAL. Idempotente: enviar o mesmo
     * payload 10 vezes deixa o recurso no mesmo estado da 1ª vez.
     * (PATCH seria parcial e NÃO é necessariamente idempotente.)
     */
    @PutMapping("/{id}")
    public EventResponse updateEvent(@PathVariable Long id,
                                     @Valid @RequestBody UpdateEventRequest request){
        return eventService.update(id, request);
    }

    /**
     * [API REST] Transição de estado como sub-recurso.
     * POST porque não é idempotente no sentido de negócio: publicar duas
     * vezes deve falhar (o segundo já não está em DRAFT).
     */
    @PostMapping("/{id}/publish")
    public EventResponse publishEvent(@PathVariable Long id){
        return eventService.publish(id);
    }

    @PostMapping("/{id}/cancel")
    public EventResponse cancelEvent(@PathVariable Long id){
        return eventService.cancel(id);
    }

    /**
     * [API REST] DELETE -> 204 No Content: deu certo, nada a devolver.
     * @ResponseStatus define o status quando o retorno é void.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@PathVariable Long id){
        eventService.delete(id);
    }
}