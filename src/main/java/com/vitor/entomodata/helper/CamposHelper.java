package com.vitor.entomodata.helper;

import org.springframework.stereotype.Component;
import com.vitor.entomodata.model.CamposAbelha;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CamposHelper {

    public Map<String, String> getTodosCampos() {
        Map<String, String> campos = new LinkedHashMap<>();
        
        for (CamposAbelha campo : CamposAbelha.values()) {
            campos.put(campo.getKey(), campo.getLabel());
        }
        
        return campos;
    }

    public Map<String, Map<String, String>> getCamposAgrupados() {
        Map<String, Map<String, String>> grupos = new LinkedHashMap<>();

        String[] ordemGrupos = {"Identificação", "Taxonomia", "Armazenamento", "Localização", "Coleta"};
        
        for (String nomeGrupo : ordemGrupos) {
            grupos.put(nomeGrupo, new LinkedHashMap<>());
        }

        for (CamposAbelha campo : CamposAbelha.values()) {
            if (campo == CamposAbelha.COD) continue; 

            String grupoNome = campo.getGrupo();
            
            grupos.putIfAbsent(grupoNome, new LinkedHashMap<>());
            
            grupos.get(grupoNome).put(campo.getKey(), campo.getLabel());
        }

        return grupos;
    }
}