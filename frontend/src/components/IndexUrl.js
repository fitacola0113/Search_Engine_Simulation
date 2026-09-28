import React, { useState } from 'react';
import { searchService } from '../services/api';
import { useNavigate } from 'react-router-dom';
import pawIcon from '../assets/paw.png'; 
import './IndexUrl.css';

function IndexUrl() {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState(null);
  const [messageType, setMessageType] = useState(''); 
  
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage(null);

    try {
      const response = await searchService.indexUrl(url);
      const data = response.data;
      
      if (data.message === 'URL metido na queue') {
        setMessage(data.message);
        setMessageType('success');
        setUrl(''); // Limpa o input apenas em sucesso
      } else {
        // Qualquer outra mensagem é tratada como erro
        setMessage(data.message || 'Erro ao indexar URL');
        setMessageType('error');
      }
    } catch (error) {
      setMessage('Erro ao indexar URL: ' + error.message);
      setMessageType('error');
    } finally {
      setLoading(false);
    }
  };

  const handleBack = () => {
    navigate(-1); 
  };

  const goHome = () => {
    navigate('/');
  };

  return (
    <div className="index-layout">
      <div className="logo-container" onClick={goHome}>
        <img src={pawIcon} alt="Logo Pata" className="paw-img" />
      </div>

      <div className="center-content">
        <h1 className="main-title">INDEXA O TEU URL</h1>

        {/* Novo container para alinhar Input e Botão lado a lado */}
        <form onSubmit={handleSubmit} className="form-container">
          
          {/* A cápsula cinzenta agora só envolve o input */}
          <div className="input-only-capsule">
            <input
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              className="url-input-clean"
              disabled={loading}
              placeholder="https://example.com" 
            />
          </div>
          
          {/* O botão está FORA da cápsula */}
          <button type="submit" className="submit-btn-round" disabled={loading}>
            {loading ? (
               <span className="loader-dots">...</span> 
            ) : (
                <svg viewBox="0 0 24 24" fill="white" className="arrow-icon">
                    <path d="M4,11V13H16L10.5,18.5L11.92,19.92L19.84,12L11.92,4.08L10.5,5.5L16,11H4Z" />
                </svg>
            )}
          </button>
        </form>

        {/* Container Absoluto para a mensagem não empurrar nada */}
        <div className="message-wrapper">
          {message && (
            <div className={`status-pill ${messageType}`}>
               {message}
            </div>
          )}
        </div>
      </div>

      <div className="footer-action">
        <button onClick={handleBack} className="back-btn-black">
          Voltar
        </button>
      </div>
    </div>
  );
}

export default IndexUrl;