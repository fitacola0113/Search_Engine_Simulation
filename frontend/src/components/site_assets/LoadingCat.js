// frontend/src/components/site_assets/LoadingCat.js
import React from 'react';
import './LoadingCat.css';
import pawIcon from '../../assets/paw_animation.png';

const LoadingCat = () => {
  return (
    <div className="paw-loader">
      <div className="paw-container">
        <img src={pawIcon} alt="paw" className="paw paw-left paw-1" />
        <img src={pawIcon} alt="paw" className="paw paw-right paw-2" />
        <img src={pawIcon} alt="paw" className="paw paw-left paw-3" />
        <img src={pawIcon} alt="paw" className="paw paw-right paw-4" />
      </div>
    </div>
  );
};

export default LoadingCat;