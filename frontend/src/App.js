import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import SearchPage from './components/SearchPage';
import SearchResults from './components/SearchResults';
import Statistics from './components/Statistics';
import IndexUrl from './components/IndexUrl';
import Backlinks from './components/Backlinks';
import { MetaBalls, CurvedDecryptedText } from './components/site_assets';
import menuStar from './assets/menu_star.png';
import './App.css';

function FloatingMenu() {
  const [isOpen, setIsOpen] = useState(false);
  const [isClosing, setIsClosing] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const handleClose = () => {
    setIsClosing(true);
    setTimeout(() => {
      setIsOpen(false);
      setIsClosing(false);
    }, 500); // Duração da animação smoothCollapse
  };

  const handleNavigate = (path) => {
    navigate(path);
    handleClose();
  };

  if (location.pathname !== '/') {
    return null;
  }

  return (
    <div className="floating-menu-container">
      <div 
        className="metaballs-wrapper"
        onClick={() => setIsOpen(true)}
        style={{ pointerEvents: isOpen ? 'none' : 'auto' }}
      >
        <MetaBalls
          color="#333333"
          cursorBallColor="#333333"
          cursorBallSize={2}
          ballCount={30}
          animationSize={20}
          enableMouseInteraction={true}
          enableTransparency={true}
          hoverSmoothness={0.05}
          clumpFactor={0.7}
          speed={0.2}
        />
      </div>

      {(isOpen || isClosing) && (
        <div className={`menu-arc ${isClosing ? 'closing' : 'open'}`}>
          <img 
            src={menuStar} 
            alt="Menu Shape" 
            className="custom-shape-image"
          />

          <button 
            className="menu-toggle"
            onClick={handleClose}
          >
            <span className="menu-icon">×</span>
          </button>

          <div className="curved-text-container">
            <svg className="curved-text-svg" viewBox="-80 100 850 470" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <path 
                  id="leftPath" 
                  d="M 60,270 A 320,320 0 0,1 220,60" 
                  fill="transparent"
                />
                <path 
                  id="centerPath" 
                  d="M 240,55 A 320,320 0 0,1 485,65" 
                  fill="transparent"
                />
                <path 
                  id="rightPath" 
                  d="M 490,70 A 320,320 0 0,1 640,270" 
                  fill="transparent"
                />
              </defs>

              {/* Textos com animação decrypt - sequencial */}
              <CurvedDecryptedText
                text="BACKLINKS"
                pathId="leftPath"
                startOffset="0%"
                textAnchor="start"
                onClick={() => handleNavigate('/backlinks')}
                speed={90}
                sequential={true}
                revealDirection="start"
              />

              <CurvedDecryptedText
                text="INDEXAR URL"
                pathId="centerPath"
                startOffset="50%"
                textAnchor="middle"
                onClick={() => handleNavigate('/index')}
                speed={90}
                sequential={true}
                revealDirection="center"
              />

              <CurvedDecryptedText
                text="STATISTICS"
                pathId="rightPath"
                startOffset="100%"
                textAnchor="end"
                onClick={() => handleNavigate('/statistics')}
                speed={90}
                sequential={true}
                revealDirection="start"
              />
            </svg>
          </div>
        </div>
      )}
    </div>
  );
}

function App() {
  return (
    <Router>
      <div className="App">
        <main className="main-content">
          <Routes>
            <Route path="/" element={<SearchPage />} />
            <Route path="/search" element={<SearchResults />} />
            <Route path="/statistics" element={<Statistics />} />
            <Route path="/index" element={<IndexUrl />} />
            <Route path="/backlinks" element={<Backlinks />} />
          </Routes>
        </main>
        
        <FloatingMenu />
      </div>
    </Router>
  );
}

export default App;