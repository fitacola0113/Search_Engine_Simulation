import { useEffect, useState, useRef } from 'react';

const CurvedDecryptedText = ({
  text,
  pathId,
  startOffset = '0%',
  textAnchor = 'start',
  className = '',
  onClick,
  delay = 0,
  speed = 30,
  maxIterations = 15,
  sequential = true,
  revealDirection = 'start',
  characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%^&*()_+'
}) => {
  const [displayText, setDisplayText] = useState('');
  const [isAnimating, setIsAnimating] = useState(false);
  const [revealedIndices, setRevealedIndices] = useState(new Set());
  const timeoutRef = useRef(null);
  
  // Inicializa com caracteres aleatórios
  useEffect(() => {
    const availableChars = characters.split('');
    const randomText = text
      .split('')
      .map(char => {
        if (char === ' ') return '\u00A0';
        return availableChars[Math.floor(Math.random() * availableChars.length)];
      })
      .join('');
    setDisplayText(randomText);
  }, [text, characters]);

  useEffect(() => {
    // Inicia a animação após o delay
    timeoutRef.current = setTimeout(() => {
      setIsAnimating(true);
    }, delay * 1000);

    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current);
    };
  }, [delay]);

  useEffect(() => {
    if (!isAnimating) return;

    let interval;
    let currentIteration = 0;

    const getNextIndex = revealedSet => {
      const textLength = text.length;
      switch (revealDirection) {
        case 'start':
          return revealedSet.size;
        case 'end':
          return textLength - 1 - revealedSet.size;
        case 'center': {
          const middle = Math.floor(textLength / 2);
          const offset = Math.floor(revealedSet.size / 2);
          const nextIndex = revealedSet.size % 2 === 0 ? middle + offset : middle - offset - 1;
          if (nextIndex >= 0 && nextIndex < textLength && !revealedSet.has(nextIndex)) {
            return nextIndex;
          }
          for (let i = 0; i < textLength; i++) {
            if (!revealedSet.has(i)) return i;
          }
          return 0;
        }
        default:
          return revealedSet.size;
      }
    };

    const availableChars = characters.split('');

    const shuffleText = (originalText, currentRevealed) => {
      return originalText
        .split('')
        .map((char, i) => {
          if (char === ' ') return '\u00A0'; // Preserva espaços
          if (currentRevealed.has(i)) return originalText[i];
          return availableChars[Math.floor(Math.random() * availableChars.length)];
        })
        .join('');
    };

    interval = setInterval(() => {
      setRevealedIndices(prevRevealed => {
        if (sequential) {
          if (prevRevealed.size < text.length) {
            const nextIndex = getNextIndex(prevRevealed);
            const newRevealed = new Set(prevRevealed);
            newRevealed.add(nextIndex);
            setDisplayText(shuffleText(text, newRevealed));
            return newRevealed;
          } else {
            clearInterval(interval);
            setDisplayText(text);
            return prevRevealed;
          }
        } else {
          setDisplayText(shuffleText(text, prevRevealed));
          currentIteration++;
          if (currentIteration >= maxIterations) {
            clearInterval(interval);
            setDisplayText(text);
          }
          return prevRevealed;
        }
      });
    }, speed);

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [isAnimating, text, speed, maxIterations, sequential, revealDirection, characters]);

  return (
    <text
      className={`curved-svg-text clickable-text ${className}`}
      onClick={onClick}
    >
      <textPath href={`#${pathId}`} startOffset={startOffset} textAnchor={textAnchor}>
        {displayText.split('').map((char, index) => {
          const isRevealed = revealedIndices.has(index);
          return (
            <tspan 
              key={`${pathId}-${index}`}
              style={{
                opacity: isRevealed ? 1 : 0.7,
                transition: 'opacity 0.3s'
              }}
            >
              {char}
            </tspan>
          );
        })}
      </textPath>
    </text>
  );
};

export default CurvedDecryptedText;