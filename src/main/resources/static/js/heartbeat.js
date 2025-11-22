(function() {
    setInterval(function() {
        fetch('/sistema/alive', { 
            method: 'POST',
            keepalive: true
        }).catch(e => {

        });
    }, 2000);
    
    console.log("❤️ Monitoramento de conexão ativo.");
})();