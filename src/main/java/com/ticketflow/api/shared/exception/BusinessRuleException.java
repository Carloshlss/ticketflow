package com.ticketflow.api.shared.exception;

/**
 * [CLEAN CODE] Violação de REGRA DE NEGÓCIO — distinta de dado inválido.
 *
 * Diferença que importa para o HTTP:
 *   - Bean Validation falhou  -> 400 Bad Request  (o payload está malformado)
 *   - Regra de negócio falhou -> 409 Conflict     (payload OK, estado impede)
 *
 * Exemplo: comprar 10 ingressos é um pedido perfeitamente válido (400 não
 * cabe), mas só existem 3 disponíveis. O estado atual do recurso conflita
 * com a operação. Isso é 409.
 */
public class BusinessRuleException extends RuntimeException {
    private final String errorCode;

    public BusinessRuleException(String message){
        this(message, "BUSINESS_RULE_VIOLATION");
    }

    /**
     * errorCode: identificador ESTÁVEL e legível por máquina.
     * O Angular vai fazer switch(errorCode), nunca comparar a mensagem —
     * mensagem muda com tradução e revisão de texto; código não.
     */
    public BusinessRuleException(String message, String errorCode){
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode(){
        return errorCode;
    }
}
