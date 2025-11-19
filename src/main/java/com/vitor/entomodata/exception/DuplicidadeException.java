package com.vitor.entomodata.exception;

import java.util.List;
import java.util.Map;

public class DuplicidadeException extends RuntimeException {
    
    private final Map<String, List<Integer>> duplicatas;

    public DuplicidadeException(Map<String, List<Integer>> duplicatas) {
        super("Foram encontradas duplicatas na planilha.");
        this.duplicatas = duplicatas;
    }

    public Map<String, List<Integer>> getDuplicatas() {
        return duplicatas;
    }
}