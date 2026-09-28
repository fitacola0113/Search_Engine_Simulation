import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './SearchPage.css';
import pawImage from '../assets/logo.png';
import { searchService } from '../services/api';

function SearchPage() {
  const [query, setQuery] = useState('');
  const [aiEnabled, setAiEnabled] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false); // ← NOVO: estado de erro
  const navigate = useNavigate();

  const handleSubmit = (e) => {
    e.preventDefault();
    if (query.trim()) {
      localStorage.setItem('aiEnabled', aiEnabled.toString());
      navigate(`/search?query=${encodeURIComponent(query)}`);
    }
  };

  const toggleAI = () => {
    setAiEnabled(!aiEnabled);
  };

  const handleHackerNews = async () => {
    setLoading(true);
    setError(false);
    
    try {
      console.log('Iniciando indexação do Hacker News...');
      const response = await searchService.fetchHackerNews();
      console.log('Resposta:', response.data);
      
      // ← Verifica se houve erro na resposta
      if (response.data.status === 'error' || response.data.status?.includes('Erro')) {
        setLoading(false);
        setError(true);
        // Remove erro após 2 segundos
        setTimeout(() => setError(false), 2000);
      } else {
        setLoading(false);
      }
    } catch (error) {
      console.error('Erro detalhado:', error);
      setLoading(false);
      setError(true);
      // Remove erro após 2 segundos
      setTimeout(() => setError(false), 2000);
    }
  };

  return (
    <div className="search-page">
      <div className="search-container">
        <div className="title-container">
          <span className="search-title">GOOG</span>
          <img src={pawImage} alt="paw" className="paw-icon" />
          <span className="search-title">L</span>
        </div>
        
        <form onSubmit={handleSubmit} className="search-form">
          
          <div 
            className={`ai-toggle-wrapper ${aiEnabled ? 'active' : ''}`} 
            onClick={toggleAI}
          >
            <div className="toggle-slot">
              <span className="ai-label">AI</span>
              <div className="toggle-button">
                <span className="status-text">{aiEnabled ? 'on' : 'off'}</span>
              </div>
            </div>
          </div>

          <div className="search-input-wrapper">
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Pesquise por uma palavra ou frase"
              className="search-input"
              autoFocus
            />
            
            {/* ========== BOTÃO NORMAL ========== */}
            {!loading && !error && (
              <button 
                type="button" 
                className="news-button"
                onClick={handleHackerNews}
              >
                <span className="news-icon">+</span>
                <span className="news-text">Top 50 Hacker News</span>
              </button>
            )}

            {/* ========== BOTÃO LOADING ========== */}
            {loading && (
              <button 
                type="button" 
                className="news-button-loading"
                disabled
              >
                <span className="loading-spinner">
                  <svg 
                    width="24" 
                    height="24" 
                    viewBox="0 0 24 24" 
                    fill="none"
                    stroke="white"
                    strokeWidth="2"
                  >
                    <circle 
                      cx="12" 
                      cy="12" 
                      r="10" 
                      stroke="white" 
                      strokeWidth="3"
                      strokeDasharray="40"
                      strokeDashoffset="15"
                      strokeLinecap="round"
                      fill="none"
                    />
                  </svg>
                </span>
                <span className="loading-text">Indexando...</span>
              </button>
            )}

            {/* ========== BOTÃO ERROR ========== */}
            {error && (
              <button 
                type="button" 
                className="news-button-error"
                disabled
              >
                <span className="error-text">ERROR</span>
              </button>
            )}
          </div>
          
          <button type="submit" className="search-button">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
              <circle cx="11" cy="11" r="8" stroke="currentColor" strokeWidth="2"/>
              <path d="M21 21l-4.35-4.35" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </button>
        </form>
      </div>
    </div>
  );
}

export default SearchPage;