import React, { useState, useEffect, useMemo } from 'react';
import { searchService } from '../services/api';
import websocketService from '../services/websocket';
import { useNavigate } from 'react-router-dom';
import pawIcon from '../assets/paw.png'; 
import './Statistics.css';

function Statistics() {
  const [stats, setStats] = useState({
    topSearches: [],
    activeBarrels: {}
  });
  
  const [selectedBarrelIndex, setSelectedBarrelIndex] = useState(-1);
  const navigate = useNavigate();

  const barrelList = useMemo(() => {
    if (!stats.activeBarrels) return [];
    // Ordena os barrels pelo nome para garantir uma ordem de navegação consistente
    return Object.entries(stats.activeBarrels)
      .map(([name, data]) => ({ name, ...data }))
      .sort((a, b) => a.name.localeCompare(b.name)); 
  }, [stats.activeBarrels]);

  useEffect(() => {
    loadInitialData();
    websocketService.connect(
      (newStats) => {
        setStats(newStats);
        // Garante que o índice selecionado ainda é válido após a atualização
        if (selectedBarrelIndex !== -1 && !newStats.activeBarrels[barrelList[selectedBarrelIndex]?.name]) {
            setSelectedBarrelIndex(-1);
        }
      },
      (error) => {
        console.error('Erro WebSocket:', error);
      }
    );
    return () => websocketService.disconnect();
  }, [selectedBarrelIndex, barrelList]); // Adicionado barrelList como dependência

  const loadInitialData = async () => {
    try {
      const response = await searchService.getStatistics();
      setStats(response.data);
    } catch (err) {
      console.error('Erro ao carregar dados:', err);
    }
  };

  const goHome = () => navigate('/');
  const goBack = () => navigate(-1);

  const handleBarrelClick = (index) => {
    setSelectedBarrelIndex(index);
  };

  const handleCloseDetail = () => {
    setSelectedBarrelIndex(-1); 
  };
  
  // Lógica para avançar/retroceder usando a moldura (clicando fora do cartão principal)
  const handleShadowClick = (e) => {
    // Verifica se o clique foi na moldura (barrel-shadow-nav) e não no cartão (detail-card)
    if (e.target.classList.contains('barrel-shadow-nav') || e.target.classList.contains('shadow-bar')) {
        let newIndex;
        
        // Se a moldura estiver no detalhe, navega
        if (selectedBarrelIndex !== -1) {
            // Se clicar na barra superior (primeira div, que é o 'previous')
            if (e.target.id === 'shadow-bar-prev') {
                newIndex = (selectedBarrelIndex - 1 + barrelList.length) % barrelList.length;
            } 
            // Se clicar na barra inferior (última div, que é o 'next')
            else if (e.target.id === 'shadow-bar-next') {
                newIndex = (selectedBarrelIndex + 1) % barrelList.length;
            } else {
                return; // Clicou no meio (ou em outro lugar)
            }
            setSelectedBarrelIndex(newIndex);
        }
    }
  };
  
  const currentBarrel = selectedBarrelIndex !== -1 ? barrelList[selectedBarrelIndex] : null;

  const gridClassName = selectedBarrelIndex !== -1 ? 'stats-content-grid detail-open' : 'stats-content-grid';

  return (
    <div className="stats-layout">
      <div className="stats-header-container">
        <div className="logo-area" onClick={goHome}>
           <img src={pawIcon} alt="Home" className="paw-icon-stats" />
        </div>
        <h1 className="stats-main-title">ESTATISTICAS</h1>
      </div>

      <div className={gridClassName}>
        
        {/* TOP 10 PESQUISAS */}
        <div className={`top-searches-card ${selectedBarrelIndex !== -1 ? 'detail-open' : ''}`}>
          <div className="card-header dark">
             <h2>TOP 10 PESQUISAS</h2>
             <div className="table-header-row">
                <span>#</span>
                <span>Termo</span>
                <span>Contagem</span>
             </div>
          </div>
          
          <div className="scrollable-list">
            {stats.topSearches && stats.topSearches.length > 0 ? (
              stats.topSearches.map((item, index) => (
                <div key={index} className="list-row">
                  <span className="rank">{index + 1}º</span>
                  <span className="term">{item.word}</span>
                  <span className="count">{item.count}</span>
                </div>
              ))
            ) : (
              <div className="no-data" style={{textAlign: 'center', padding: '2rem'}}>A carregar...</div>
            )}
          </div>
        </div>

        {/* BARRELS */}
        <div className="barrels-column">
          
          {selectedBarrelIndex === -1 ? (
            <div className="barrels-list-view">
              {barrelList.length > 0 ? (
                barrelList.map((barrel, index) => (
                  <div 
                    key={barrel.name} 
                    className="barrel-card clickable"
                    onClick={() => handleBarrelClick(index)}
                  >
                    <div className="barrel-info">
                      <h3>Barrel: {barrel.name}</h3>
                      <div className="big-numbers">
                        <span>{barrel.indexSize ? barrel.indexSize.toLocaleString() : 0}</span>
                        <span>{barrel.averageSearchTime ? barrel.averageSearchTime.toFixed(8) : '0.00000000'} ds</span> 
                      </div>
                    </div>
                    <div className={`status-bar ${barrel.status === 'down' ? 'inactive' : 'active'}`}>
                       Estado: {barrel.status === 'down' ? 'INATIVO' : 'ATIVO'}
                    </div>
                  </div>
                ))
              ) : (
                <div className="no-data-barrel" style={{textAlign: 'center', color: '#999'}}>Sem barrels ativos</div>
              )}
            </div>
          ) : (
            <div className="barrel-detail-view">
              
              {/* NOVO: Moldura/Shadow de Navegação (Substitui as Abas) */}
              <div 
                className="barrel-shadow-nav"
                onClick={handleShadowClick}
              >
                  {/* Div superior para navegar para o barrel anterior */}
                  <div id="shadow-bar-prev" className="shadow-bar" title="Barrel Anterior"></div>
                  
                  {/* O Cartão principal estará aqui, no meio */}
                  
                  {/* Div inferior para navegar para o próximo barrel */}
                  <div id="shadow-bar-next" className="shadow-bar" title="Próximo Barrel"></div>

              </div>

              {/* Cartão de Detalhe Fixo - Posicionado sobre a moldura */}
              {currentBarrel && (
                  <div 
                    className="barrel-card detail-card clickable" 
                    onClick={handleCloseDetail}
                  >
                    <div className="barrel-info detail-info">
                        <h3>Barrel: {currentBarrel.name}</h3>
                        
                        <div className="big-numbers detail-numbers">
                        {/* Índice - Esquerda */}
                          <span>{currentBarrel.indexSize?.toLocaleString()}</span>
                          {/* Tempo Médio - Direita (ds) */}
                          <span>{currentBarrel.averageSearchTime ? `${currentBarrel.averageSearchTime.toFixed(8)} ds` : '0 ds'}</span>
                        </div>
                    </div>
                    
                    {/* Barra de Estado (Visível) */}
                    <div className={`status-bar detail ${currentBarrel.status === 'down' ? 'inactive' : 'active'}`}>
                        Estado: {currentBarrel.status === 'down' ? 'INATIVO' : 'ATIVO'}
                    </div>
                  </div>
              )}
              

              <div className="click-hint">Clica no cartão para voltar à lista</div>
            </div>
          )}
        </div>

      </div>

      <div className="footer-area">
        <button onClick={goBack} className="back-btn-stats">Voltar</button>
      </div>
    </div>
  );
}

export default Statistics;