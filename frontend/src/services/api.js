// frontend/src/services/api.js
import axios from 'axios';

// ============================================================
// CONFIGURAÇÃO DO BACKEND
// ============================================================
// Em desenvolvimento (npm start): proxy automático para https://localhost:8443
// Em produção (npm build): usa caminho relativo /api

const API_URL = process.env.NODE_ENV === 'production' 
  ? '/api'  // ← Produção: caminho relativo
  : '/api'; // ← Desenvolvimento: proxy trata disto (definido em package.json)

// Cria instância do axios com configuração base
const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  // Remove validateStatus - axios lida com isto automaticamente
});

// ============================================================
// SERVIÇOS DA API
// ============================================================
export const searchService = {
  /**
   * Pesquisar palavras
   * @param {string} query - Termo de pesquisa
   * @param {number} page - Número da página (default: 1)
   * @param {boolean} aiEnabled - Ativar resumo IA (default: false)
   */
  search: (query, page = 1, aiEnabled = false) => 
    api.get('/search', { 
      params: { query, page, aiEnabled } 
    }),
  
  /**
   * Carregar mais resultados (paginação)
   * @param {number} page - Número da página
   */
  loadMore: (page = 2) => 
    api.get('/search/more', { 
      params: { page } 
    }),
  
  /**
   * Indexar novo URL
   * @param {string} url - URL para indexar
   */
  indexUrl: (url) => 
    api.post('/index', null, { 
      params: { url } 
    }),
  
  /**
   * Obter backlinks de um URL
   * @param {string} url - URL alvo
   * @param {number} page - Número da página
   */
  getBacklinks: (url, page = 1) => 
    api.get('/backlinks', { 
      params: { url, page } 
    }),
  
  /**
   * Carregar mais backlinks (paginação)
   * @param {number} page - Número da página
   */
  loadMoreBacklinks: (page = 2) => 
    api.get('/backlinks/more', { 
      params: { page } 
    }),
  
  /**
   * Obter estatísticas do sistema
   */
  getStatistics: () => 
    api.get('/statistics'),
  
  /**
   * Indexar top 50 stories do Hacker News
   */
  fetchHackerNews: () => 
    api.post('/hackernews')
};

export default api;