package com.ticketflow.api.shared.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component // Faz o Spring registrar o filtro automaticamente na fila do Tomcat
/**
 * @Order define a POSIÇÃO na cadeia de filtros.
 * Filtros são executados em ordem crescente. Queremos ser o PRIMEIRO,
 * para que TODO log — inclusive os dos filtros de segurança —
 * já tenha o requestId. HIGHEST_PRECEDENCE = Integer.MIN_VALUE.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER_NAME = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException{
        try{
            // 1. Tenta pegar o ID se o front-end já mandou, se não, gera um novo aleatório
            String requestId = request.getHeader(HEADER_NAME);
            if (!isValidRequestId(requestId)){
                requestId = UUID.randomUUID().toString();
            }

            // 2. Coloca no MDC (Map de Diagnóstico do Log do Java)
            // A partir daqui, o SLF4J sabe o ID dessa requisição nesta Thread
            MDC.put(MDC_KEY, requestId);

            // 3. Devolve o ID no cabeçalho da resposta para o cliente saber qual foi o ID dele
            response.setHeader(HEADER_NAME, requestId);

            // 4. Deixa a requisição seguir viagem para o Controller
            filterChain.doFilter(request, response);
        } finally {
            // 5. OBRIGATÓRIO: Limpa o ID da memória ao terminar a requisição.
            // Como o Tomcat reutiliza Threads, se você não limpar, o ID do João pode "vazar" para o log da Maria.
            // ✅ MELHORIA 2 — remove APENAS a nossa chave, em vez de MDC.clear().
            // Na Fase 7 o Spring Security e o Micrometer Tracing colocarão as
            // próprias chaves no MDC (userId, traceId, spanId). O clear() apagaria
            // as deles e quebraria o rastreamento alheio. Limpe só o que você criou.
            MDC.remove(MDC_KEY);
        }
    }

    private boolean isValidRequestId(String value){
        return value != null
                && !value.isBlank()
                && value.length() <= 64
                // Whitelist de caracteres: só o que é seguro em arquivo de log
                && value.matches("[a-zA-Z0-9\\-_]+");
    }
}
