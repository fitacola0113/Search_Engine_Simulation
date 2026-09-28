import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import LoadingCat from './site_assets/LoadingCat'; 
import { motion, useInView } from 'motion/react';
import { searchService } from '../services/api';
import GlassSurface from './site_assets';
import pawIcon from '../assets/logo.png';
import './SearchResults.css';

const AnimatedResultCard = ({ children, delay = 0, index }) => {
  const ref = useRef(null);
  const inView = useInView(ref, { amount: 0.3, once: false });
  
  return (
    <motion.div
      ref={ref}
      data-index={index}
      initial={{ scale: 0.95, opacity: 0, y: 20 }}
      animate={inView ? { scale: 1, opacity: 1, y: 0 } : { scale: 0.95, opacity: 0, y: 20 }}
      transition={{ duration: 0.3, delay }}
    >
      {children}
    </motion.div>
  );
};

function SearchResults() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get('query');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
    const [aiSummary, setAiSummary] = useState(null); 
  const [topGradientOpacity, setTopGradientOpacity] = useState(0);
  const [bottomGradientOpacity, setBottomGradientOpacity] = useState(1);
  const scrollRef = useRef(null);

  const performSearch = useCallback(async (page = 1, aiEnabled = false) => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await searchService.search(query, page, aiEnabled);
      const data = response.data;
      
      if (data.error) {
        setError(data.error);
      } else {
        setResults(data.results || []);
        setTotalPages(data.totalPages || 0);
        setCurrentPage(data.currentPage || 1);
        setAiSummary(data.aiSummary || null);
      }
    } catch (err) {
      setError('Erro ao realizar pesquisa: ' + err.message);
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    if (query) {
      // Lê o estado do AI do localStorage (passado do SearchPage)
      const aiEnabled = localStorage.getItem('aiEnabled') === 'true';
      performSearch(1, aiEnabled);
    }
  }, [query, performSearch]);

  const goToPage = (page) => {
    if (page !== currentPage && page > 0 && page <= totalPages) {
      performSearch(page);
      if (scrollRef.current) {
        scrollRef.current.scrollTo({ top: 0, behavior: 'smooth' });
      }
    }
  };

  const handleScroll = useCallback((e) => {
    const { scrollTop, scrollHeight, clientHeight } = e.target;
    
    setTopGradientOpacity(Math.min(scrollTop / 100, 1));
    
    const bottomDistance = scrollHeight - (scrollTop + clientHeight);
    setBottomGradientOpacity(
      scrollHeight <= clientHeight ? 0 : Math.min(bottomDistance / 100, 1)
    );
  }, []);

  if (loading) {
    return (
      <div className="loading">
        <LoadingCat />
        <div className="loading-text">
          Pesquisando<span className="loading-dots"></span>
        </div>
      </div>
    );
  }

  return (
    <div className="search-results-layout">
      {/* Header com Logo e Título em Glass Surface */}
      <header className="results-header-fixed">
        <GlassSurface 
          width="100%"
          height={120}
          borderRadius={0}
          backgroundOpacity={0.1}
          Saturation = {1.9}
          Border Width = {0.05}
          brightness={55}
          opacity={0.82}
          blur={8}
          Displace = {40}
          distortionScale = {-230}
          className="glass-header"
        >
          <div className="header-content">
            <Link to="/" className="logo-link">
              <img src={pawIcon} alt="Paw" className="logo-paw" />
            </Link>
            <h1 className="header-title">GOOGOL</h1>
          </div>
        </GlassSurface>
      </header>

      {/* Área com scroll dos resultados */}
      <div 
        ref={scrollRef}
        className="scrollable-results"
        onScroll={handleScroll}
      >
        {error && (
          <div className="error-message">{error}</div>
        )}

        {!error && results.length === 0 && (
          <div className="no-results">
            <p>Nenhum resultado encontrado para "{query}".</p>
          </div>
        )}

        {aiSummary && (
          <div className="ai-summary-container">
            <div className="ai-summary-header">
              <span className="ai-badge">AI</span>
              <h2>Resumo Gerado por IA</h2>
            </div>
            <div className="ai-summary-content">
              {aiSummary}
            </div>
          </div>
        )}

        <div className="results-container">
          {results.map((result, index) => (
            <AnimatedResultCard key={index} index={index} delay={index * 0.05}>
              <article className="result-card">
                <div className="url-bar">
                  <a 
                    href={result.url} 
                    target="_blank" 
                    rel="noopener noreferrer"
                    className="url-text"
                  >
                    {result.url}
                  </a>
                  <div className="reference-badge">
                    <span className="reference-text">Referências: {result.referenceCount || 0}</span>
                  </div>
                </div>
                
                <div className="card-content">
                  <h2 className="result-title">
                    {result.title || 'Sem título'}
                  </h2>
                  
                  <p className="result-snippet">
                    {result.snippet || 'Sem descrição disponível'}
                  </p>
                </div>
              </article>
            </AnimatedResultCard>
          ))}
        </div>

        {totalPages > 1 && (
          <div className="pagination">
            <button 
              className="page-nav" 
              disabled={currentPage === 1}
              onClick={() => goToPage(currentPage - 1)}
            >
              ←
            </button>
            
            <button 
              className={`page-number ${currentPage === 1 ? 'active' : ''}`}
              onClick={() => goToPage(1)}
            >
              1
            </button>
            
            {currentPage > 3 && <span className="page-dots">...</span>}
            
            {currentPage > 2 && currentPage < totalPages && (
              <button className="page-number active">
                {currentPage}
              </button>
            )}
            
            {currentPage < totalPages - 1 && currentPage > 1 && (
              <button 
                className="page-number"
                onClick={() => goToPage(currentPage + 1)}
              >
                {currentPage + 1}
              </button>
            )}
            
            {currentPage < totalPages - 2 && <span className="page-dots">...</span>}
            
            {totalPages > 1 && (
              <button 
                className={`page-number ${currentPage === totalPages ? 'active' : ''}`}
                onClick={() => goToPage(totalPages)}
              >
                {totalPages}
              </button>
            )}
            
            <button 
              className="page-nav" 
              disabled={currentPage === totalPages}
              onClick={() => goToPage(currentPage + 1)}
            >
              →
            </button>
          </div>
        )}

        <div 
          className="scroll-gradient-top" 
          style={{ opacity: topGradientOpacity }}
        />
        <div 
          className="scroll-gradient-bottom" 
          style={{ opacity: bottomGradientOpacity }}
        />
      </div>

      <footer className="results-footer-fixed">
        <Link to="/" className="back-button">
          Voltar
        </Link>
      </footer>
    </div>
  );
}

export default SearchResults;