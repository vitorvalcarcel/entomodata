package com.vitor.entomodata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.vitor.entomodata.model.Exemplar;
import com.vitor.entomodata.service.ExemplarService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ExemplarController {

    @Autowired
    private ExemplarService service;

    @GetMapping("/")
    public String listarExemplares(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "cod") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Exemplar filtro
    ) {
        buscarDados(model, page, size, sort, dir, filtro);
        return "index";
    }

    @GetMapping("/filtrar")
    public String filtrarExemplares(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "cod") String sort,
            @RequestParam(defaultValue = "asc") String dir,
            Exemplar filtro
    ) {
        buscarDados(model, page, size, sort, dir, filtro);
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

    @GetMapping("/novo")
    public String mostrarFormulario(Model model) {
        model.addAttribute("exemplar", new Exemplar());
        model.addAttribute("edicao", false); 
        return "cadastro";
    }

    @GetMapping("/editar/{cod}")
    public String editarExemplar(@PathVariable String cod, Model model) {
        Exemplar exemplar = service.buscarPorId(cod);
        if (exemplar == null) {
            return "redirect:/";
        }
        model.addAttribute("exemplar", exemplar);
        model.addAttribute("edicao", true);
        return "cadastro";
    }
    
    @PostMapping("/editar/conflito")
    public String editarConflitoCadastro(Exemplar exemplar, Model model) {
        model.addAttribute("exemplar", exemplar);
        model.addAttribute("edicao", true);
        return "cadastro";
    }

    @PostMapping("/salvar")
    public String salvarExemplar(Exemplar exemplar, @RequestParam(defaultValue = "false") boolean isEdit, Model model) {
        if (!isEdit && service.buscarPorId(exemplar.getCod()) != null) {
            Exemplar existente = service.buscarPorId(exemplar.getCod());
            
            model.addAttribute("novo", exemplar);
            model.addAttribute("antigo", existente);
            
            return "cadastro-conflito";
        }

        service.salvar(exemplar);
        return "redirect:/";
    }


    @GetMapping("/deletar/{cod}")
    public String deletarExemplarIndividual(@PathVariable String cod) {
        service.deletarPorListaDeCodigos(Arrays.asList(cod));
        return "redirect:/";
    }

    @GetMapping("/deletar")
    public String telaDeletar() {
        return "deletar-busca";
    }

    @PostMapping("/deletar/verificar")
    public String verificarExclusao(@RequestParam("codigosRaw") String codigosRaw, Model model) {
        List<String> codigosDigitados = Arrays.stream(codigosRaw.split("[\\r\\n,]+"))
                                              .map(String::trim)
                                              .filter(s -> !s.isEmpty())
                                              .collect(Collectors.toList());

        if (codigosDigitados.isEmpty()) {
            model.addAttribute("erro", "Nenhum código foi informado.");
            return "deletar-busca";
        }

        List<Exemplar> encontrados = service.buscarPorListaDeCodigos(codigosDigitados);
        List<String> idsEncontrados = encontrados.stream().map(Exemplar::getCod).collect(Collectors.toList());
        List<String> naoEncontrados = codigosDigitados.stream()
                                                      .filter(c -> !idsEncontrados.contains(c))
                                                      .collect(Collectors.toList());

        model.addAttribute("encontrados", encontrados);
        model.addAttribute("naoEncontrados", naoEncontrados);
        model.addAttribute("qtdParaApagar", encontrados.size());

        return "deletar-confirma";
    }

    @PostMapping("/deletar/confirmar")
    public String confirmarExclusao(
            @RequestParam("idsParaDeletar") List<String> ids,
            @RequestParam("senhaConfirmacao") int senhaDigitada,
            @RequestParam("qtdReal") int qtdReal,
            Model model
    ) {
        if (senhaDigitada != qtdReal) {
            List<Exemplar> encontrados = service.buscarPorListaDeCodigos(ids);
            model.addAttribute("encontrados", encontrados);
            model.addAttribute("qtdParaApagar", qtdReal);
            model.addAttribute("naoEncontrados", new java.util.ArrayList<>()); // Corrige o bug do NULL
            model.addAttribute("erro", "Olha, você digitou o número errado. Você sabe o que tá fazendo? Presta atenção!");
            return "deletar-confirma"; 
        }

        service.deletarPorListaDeCodigos(ids);
        return "redirect:/?msg=ExclusaoSucesso";
    }
}