package com.vitor.entomodata.model;

public enum CamposAbelha {

    COD("cod", "Código (ID)", "Identificação"),

    ESPECIE("especie", "Espécie", "Taxonomia"),
    SEXO("sexo", "Sexo", "Taxonomia"),
    FAMILIA("familia", "Família", "Taxonomia"),
    SUBFAMILIA("subfamilia", "Subfamília", "Taxonomia"),
    TRIBO("tribo", "Tribo", "Taxonomia"),
    SUBTRIBO("subtribo", "Subtribo", "Taxonomia"),
    GENERO("genero", "Gênero", "Taxonomia"),
    SUBGENERO("subgenero", "Subgênero", "Taxonomia"),
    SUBESPECIE("subespecie", "Subespécie", "Taxonomia"),
    AUTOR("autor", "Autor", "Taxonomia"),
    DETERMINADOR("determinador", "Determinador", "Taxonomia"),
    ESPECIE_VEGETAL("especieVegetalAssociada", "Planta Assoc.", "Taxonomia"),

    GAVETA("gaveta", "Gaveta", "Armazenamento"),
    CAIXA("caixa", "Caixa", "Armazenamento"),

    PAIS("pais", "País", "Localização"),
    ESTADO("estado", "Estado", "Localização"),
    CIDADE("cidade", "Cidade", "Localização"),
    LOCALIDADE("localidade", "Localidade", "Localização"),
    PROPRIETARIO("proprietarioDoLocalDeColeta", "Proprietário", "Localização"),
    BIOMA("bioma", "Bioma", "Localização"),
    LATITUDE("latitude", "Lat", "Localização"),
    LONGITUDE("longitude", "Long", "Localização"),

    COLETOR("coletor", "Coletor", "Coleta"),
    DATA("data", "Data", "Coleta"),
    HORARIO("horarioColeta", "Horário", "Coleta"),
    METODO("metodoDeAquisicao", "Método", "Coleta");

    private final String key;
    private final String label;
    private final String grupo;

    CamposAbelha(String key, String label, String grupo) {
        this.key = key;
        this.label = label;
        this.grupo = grupo;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getGrupo() { return grupo; }

    public static CamposAbelha fromKey(String key) {
        for (CamposAbelha campo : values()) {
            if (campo.key.equals(key)) {
                return campo;
            }
        }
        return null;
    }
}