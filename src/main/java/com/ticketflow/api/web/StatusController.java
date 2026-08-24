package com.ticketflow.api.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * [API REST / SPRING MVC] @RestController = @Controller + @ResponseBody.
 * Significa: esta classe atende requisições HTTP e o retorno dos métodos
 * é serializado direto para o corpo da resposta (JSON, via Jackson),
 * em vez de ser tratado como nome de página HTML.
 */
@RestController
// [API REST] Prefixo comum a todos os endpoints da classe.
// O "/v1" é VERSIONAMENTO de API: permite lançar /v2 sem quebrar clientes antigos.
@RequestMapping("/api/v1/status")
public class StatusController {

    /**
     * [API REST] GET é o verbo de LEITURA. Deve ser:
     *  - seguro    (não altera estado no servidor)
     *  - idempotente (chamar 1x ou 100x dá o mesmo resultado)
     * Guarde a palavra "idempotente": ela é o tema central da Fase 8.
     *
     * Teste em: http://localhost:8080/api/v1/status
     */
    @GetMapping
    public StatusResponse status(){
        // [JAVA 21 - RECORD] Instanciação simples; o record já tem construtor canônico.
        return new StatusResponse("UP", "ticketflow-api", "v1", Instant.now());
    }

    /**
     * [API REST] @PathVariable extrai um pedaço da URL.
     * Usado quando o valor IDENTIFICA o recurso: /status/echo/oi
     *
     * (Compare: @RequestParam extrai da query string, /status?nome=oi —
     *  usado para filtro/ordenação/paginação. Veremos na Fase 3.)
     *
     * [SPRING MVC] ResponseEntity dá controle total sobre status code e headers.
     * Prefira ResponseEntity quando o status varia; retorne o objeto puro
     * quando é sempre 200.
     */
    @GetMapping("/echo/{message}")
    public ResponseEntity<StatusResponse> echo(@PathVariable String message){
        return ResponseEntity
                .status(HttpStatus.OK)
                .header("X-Ticketflow-Fase", "1")
                .body(new StatusResponse(message, "ticketflow-api", "v1", Instant.now()));
    }

    /**
     * [JAVA 21 - RECORD] Grande novidade vinda do Java 8!
     * Um record é uma classe IMUTÁVEL de dados. Esta única linha gera:
     *   - campos private final
     *   - construtor com todos os args
     *   - métodos de acesso: status(), servico()... (SEM o prefixo "get")
     *   - equals(), hashCode() e toString()
     *
     * No Java 8 isso seria ~40 linhas ou um @Data do Lombok.
     * [CLEAN CODE] Records são perfeitos para DTOs — imutáveis por natureza,
     * o que elimina uma classe inteira de bugs. Fase 3 usa isso pra valer.
     */
    public record StatusResponse(String status, String sevice, String apiVersion, Instant timestamp){

    }
}
