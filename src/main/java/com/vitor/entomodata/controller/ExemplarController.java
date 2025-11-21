package com.vitor.entomodata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.vitor.entomodata.model.Exemplar;
import com.vitor.entomodata.service.ExemplarService;
import com.vitor.entomodata.helper.CamposHelper;
import com.vitor.entomodata.helper.ExcelHelper;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ExemplarController {

    @Autowired
    private ExemplarService service;
    
    @Autowired
    private CamposHelper camposHelper;
    
    @Autowired
    private ExcelHelper excelHelper;

    // Método auxiliar para injetar helpers em toda view
    private void adicionarHelpers(Model model) {
        model.addAttribute("camposHelper", camposHelper);
    }

    @GetMapping("/")
    public String listarExemplares(Model model, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size, @RequestParam(defaultValue = "cod") String sort, @RequestParam(defaultValue = "asc") String dir, Exemplar filtro) {
        buscarDados(model, page, size, sort, dir, filtro);
        adicionarHelpers(model);
        return "index";
    }

    @GetMapping("/filtrar")
    public String filtrarExemplares(Model model, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size, @RequestParam(defaultValue = "cod") String sort, @RequestParam(defaultValue = "asc") String dir, Exemplar filtro) {
        buscarDados(model, page, size, sort, dir, filtro);
        // Importante: O fragmento AJAX também precisa do helper se for renderizar o cabeçalho!
        adicionarHelpers(model); 
        return "index :: tabela-resultados";
    }

    private void buscarDados(Model model, int page, int size, String sort, String dir, Exemplar filtro) {
        Page<Exemplar> paginaDeAbelhas = service.buscarTodosPaginado(page, size, sort, dir, filtro);
        model.addAttribute("listaDeAbelhas", paginaDeAbelhas);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", paginaDeAbelhas.getTotalPages());
        model.addAttribute("totalItems", paginaDeAbelhas.getTotalElements());
        model.addAttribute("filtro", filtro);
        model.addAttribute("sortField", sort);
        model.addAttribute("sortDir", dir);
    }
    
    // === EXPORTAÇÃO ===
    
    @GetMapping("/exportar")
    public ResponseEntity<InputStreamResource> exportarExcel() {
        List<Exemplar> dados = service.buscarTodos();
        ByteArrayInputStream in = excelHelper.gerarPlanilhaExemplares(dados, camposHelper.getTodosCampos());
        
        String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String nomeArquivo = "entomodata_export_" + dataHora + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + nomeArquivo)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    // === CADASTRO (NOVO) ===

    @GetMapping("/novo")
    public String mostrarFormulario(Model model) {
        model.addAttribute("exemplar", new Exemplar());
        adicionarHelpers(model); // Necessário para gerar o form dinâmico
        return "cadastro"; 
    }

    @PostMapping("/salvar")
    public String salvarExemplar(Exemplar exemplar, Model model) {
        if (service.buscarPorId(exemplar.getCod()) != null) {
            Exemplar existente = service.buscarPorId(exemplar.getCod());
            model.addAttribute("novo", exemplar);
            model.addAttribute("antigo", existente);
            adicionarHelpers(model);
            return "cadastro-conflito";
        }
        service.salvar(exemplar);
        return "redirect:/";
    }

    // === EDIÇÃO ===

    @GetMapping("/editar/{cod}")
    public String editarExemplar(@PathVariable String cod, Model model) {
        Exemplar original = service.buscarPorId(cod);
        if (original == null) return "redirect:/";
        model.addAttribute("exemplar", original); 
        model.addAttribute("original", original); 
        adicionarHelpers(model); // Necessário para o form de edição
        return "editar";
    }
    
    @PostMapping("/editar/conflito")
    public String editarConflitoCadastro(Exemplar exemplar, Model model) {
        Exemplar original = service.buscarPorId(exemplar.getCod());
        model.addAttribute("exemplar", exemplar);
        model.addAttribute("original", original);
        adicionarHelpers(model);
        return "editar"; 
    }

    @PostMapping("/editar/revisar")
    public String revisarEdicao(Exemplar exemplar, Model model) {
        Exemplar original = service.buscarPorId(exemplar.getCod());
        if (original == null) { service.salvar(exemplar); return "redirect:/"; }
        model.addAttribute("novo", exemplar);
        model.addAttribute("antigo", original);
        adicionarHelpers(model); // Para a tabela comparativa
        return "edicao-confirmar";
    }

    @PostMapping("/editar/retornar")
    public String retornarParaEdicao(Exemplar exemplar, Model model) {
        Exemplar original = service.buscarPorId(exemplar.getCod());
        if (original == null) original = new Exemplar();
        model.addAttribute("exemplar", exemplar); 
        model.addAttribute("original", original);
        adicionarHelpers(model);
        return "editar"; 
    }

    @PostMapping("/editar/confirmar")
    public String confirmarEdicao(Exemplar exemplar) {
        service.salvar(exemplar);
        return "redirect:/?msg=EdicaoSucesso";
    }

    // === EXCLUSÃO ===
    
    @GetMapping("/deletar/{cod}")
    public String deletarExemplarIndividual(@PathVariable String cod) {
        service.deletarPorListaDeCodigos(Arrays.asList(cod));
        return "redirect:/";
    }

    @GetMapping("/deletar")
    public String telaDeletar() { return "deletar-busca"; }

    @PostMapping("/deletar/verificar")
    public String verificarExclusao(@RequestParam("codigosRaw") String codigosRaw, Model model) {
        List<String> codigosDigitados = Arrays.stream(codigosRaw.split("[\\r\\n,]+")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (codigosDigitados.isEmpty()) { model.addAttribute("erro", "Nenhum código foi informado."); return "deletar-busca"; }
        List<Exemplar> encontrados = service.buscarPorListaDeCodigos(codigosDigitados);
        List<String> idsEncontrados = encontrados.stream().map(Exemplar::getCod).collect(Collectors.toList());
        List<String> naoEncontrados = codigosDigitados.stream().filter(c -> !idsEncontrados.contains(c)).collect(Collectors.toList());
        model.addAttribute("encontrados", encontrados);
        model.addAttribute("naoEncontrados", naoEncontrados);
        model.addAttribute("qtdParaApagar", encontrados.size());
        return "deletar-confirma";
    }

    @PostMapping("/deletar/confirmar")
    public String confirmarExclusao(@RequestParam("idsParaDeletar") List<String> ids, @RequestParam("senhaConfirmacao") int senhaDigitada, @RequestParam("qtdReal") int qtdReal, Model model) {
        if (senhaDigitada != qtdReal) {
            List<Exemplar> encontrados = service.buscarPorListaDeCodigos(ids);
            model.addAttribute("encontrados", encontrados);
            model.addAttribute("qtdParaApagar", qtdReal);
            model.addAttribute("naoEncontrados", new ArrayList<>());
            model.addAttribute("erro", "Olha, você digitou o número errado. Você sabe o que tá fazendo? Presta atenção!");
            return "deletar-confirma"; 
        }
        service.deletarPorListaDeCodigos(ids);
        return "redirect:/?msg=ExclusaoSucesso";
    }
}