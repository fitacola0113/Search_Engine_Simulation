import React, { useState, useCallback, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { searchService } from '../services/api';
import GlassSurface from './site_assets';
import { motion, useInView } from 'motion/react';
import pawIcon from '../assets/logo.png';
import './Backlinks.css';

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

function Backlinks() {
  const [url, setUrl] = useState('');
  const [targetUrl, setTargetUrl] = useState('');
  const [backlinks, setBacklinks] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [showResults, setShowResults] = useState(false);
  const [topGradientOpacity, setTopGradientOpacity] = useState(0);
  const [bottomGradientOpacity, setBottomGradientOpacity] = useState(1);
  const scrollRef = useRef(null);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setBacklinks([]);
    setShowResults(false);

    try {
      const response = await searchService.getBacklinks(url, 1);
      const data = response.data;

      if (data.error) {
        setError(data.error);
      } else {
        setTargetUrl(data.targetUrl);
        setBacklinks(data.backlinks || []);
        setTotalPages(data.totalPages || 0);
        setCurrentPage(data.currentPage || 1);
        setShowResults(true);
      }
    } catch (err) {
      setError('Erro ao buscar backlinks: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const goToPage = async (page) => {
    if (page === currentPage || page < 1 || page > totalPages) return;

    setLoading(true);
    try {
      const response = await searchService.getBacklinks(targetUrl, page);
      const data = response.data;

      if (data.backlinks && data.backlinks.length > 0) {
        setBacklinks(data.backlinks);
        setCurrentPage(page);
        if (scrollRef.current) {
          scrollRef.current.scrollTo({ top: 0, behavior: 'smooth' });
        }
      }
    } catch (err) {
      console.error('Erro ao carregar página:', err);
    } finally {
      setLoading(false);
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

  if (loading && !showResults) {
    return (
      <div className="backlinks-loading">
        <div className="loading-spinner"></div>
        <div className="loading-text">
          Buscando backlinks<span className="loading-dots"></span>
        </div>
      </div>
    );
  }

  return (
    <div className="backlinks-layout">
      {/* Logo sempre visível no canto superior esquerdo */}
      <Link to="/" className="logo-link-fixed">
        <img src={pawIcon} alt="Paw" className="logo-paw" />
      </Link>

      {/* Header com Título em Glass Surface - APENAS quando showResults é true */}
      {showResults && (
        <header className="backlinks-header-fixed">
          <GlassSurface 
            width="100%"
            height={120}
            borderRadius={0}
            backgroundOpacity={0.1}
            Saturation={1.9}
            BorderWidth={0.05}
            brightness={55}
            opacity={0.82}
            blur={8}
            Displace={40}
            distortionScale={-230}
            className="glass-header"
          >
            <div className="header-content">
              <h1 className="header-title">BACKLINKS</h1>
            </div>
          </GlassSurface>
        </header>
      )}

      {/* Área com scroll */}
      <div 
        ref={scrollRef}
        className={`scrollable-backlinks ${!showResults ? 'no-header' : ''}`}
        onScroll={handleScroll}
      >
        {/* Formulário de busca */}
        {!showResults && (
          <div className="search-form-container">
            <h2 className="form-title">REFERÊNCIAS DO TEU URL</h2>
            <form onSubmit={handleSubmit} className="backlinks-form">
              <input
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                className="url-input"
                placeholder="https://example.com"
                required
              />
              <button type="submit" className="submit-button">
                <svg viewBox="0 0 24 24" fill="white" className="arrow-icon">
                  <path d="M4,11V13H16L10.5,18.5L11.92,19.92L19.84,12L11.92,4.08L10.5,5.5L16,11H4Z" />
                </svg>
              </button>
            </form>
          </div>
        )}

        {/* Resultados */}
        {showResults && backlinks.length > 0 && (
          <>
            <div className="target-url-container">
              <p className="target-url-label">Backlinks para:</p>
              <a href={targetUrl} target="_blank" rel="noopener noreferrer" className="target-url-link">
                {targetUrl}
              </a>
            </div>

            <div className="backlinks-container">
              {backlinks.map((link, index) => (
                <AnimatedResultCard key={index} index={index} delay={index * 0.05}>
                  <article className="backlink-card">
                    <div className="url-bar">
                      <a 
                        href={link.url} 
                        target="_blank" 
                        rel="noopener noreferrer"
                        className="url-text"
                      >
                        {link.url}
                      </a>
                    </div>
                    
                    <div className="card-content">
                      <h2 className="backlink-title">
                        {link.title || 'Sem título'}
                      </h2>
                      
                      <p className="backlink-snippet">
                        {link.snippet || 'Sem descrição disponível'}
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
          </>
        )}

        {/* Mensagens de erro ou sem resultados */}
        {error && (
          <div className="error-message">{error}</div>
        )}

        {showResults && backlinks.length === 0 && !error && (
          <div className="no-results">
            <p>Nenhum backlink encontrado para "{targetUrl}".</p>
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

      {/* Footer fixo */}
      <footer className="backlinks-footer-fixed">
        <button onClick={() => navigate('/')} className="back-button">
          Voltar
        </button>
      </footer>
    </div>
  );
}

export default Backlinks;