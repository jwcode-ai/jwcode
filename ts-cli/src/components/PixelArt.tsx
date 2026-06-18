// Pure box-drawing + ASCII pixel animals. Each frame is a multi-line string;
// no emoji/sixel so it renders across terminals. Note: a frame's last line
// must NOT end with a backslash (it would escape the closing backtick).
import type { AnimalVariant } from '../hooks/useAsciiAnimation.js';

const cat: AnimalVariant = {
  id: 'cat',
  name: 'Cat',
  tip: 'Curious and quick -- type / for commands',
  frames: [
    String.raw` /\_/\
( o.o )
 > ^ <`,
    String.raw` /\_/\
( -.- )
 > ^ <`,
    String.raw` /\_/\
( o.o )
 > w <`,
  ],
};

const dog: AnimalVariant = {
  id: 'dog',
  name: 'Dog',
  tip: 'Loyal helper -- @ to reference files',
  frames: [
    String.raw`  __
 /  \
( o.o )__
 \__/_/`,
    String.raw`  __
 /  \
( o.o )__
 \__/_/~`,
    String.raw`  __
 /  \
( o.o )__
 \__/_/~>`,
  ],
};

const rabbit: AnimalVariant = {
  id: 'rabbit',
  name: 'Rabbit',
  tip: 'Hops between tasks -- Tab toggles plan mode',
  frames: [
    String.raw` (\(\
( -.-)
 o(")(")`,
    String.raw` (\(\
( x.x)
 o(")(")`,
    String.raw` (\(\
( -.-)
 O(")(")`,
  ],
};

const bird: AnimalVariant = {
  id: 'bird',
  name: 'Bird',
  tip: 'Light on its feet -- Esc pauses generation',
  frames: [
    String.raw`  (o>
  //\
  V/_/`,
    String.raw`  (o>
  //\
  \/_/`,
    String.raw`  (o>
  //\
  V/_/`,
  ],
};

const bear: AnimalVariant = {
  id: 'bear',
  name: 'Bear',
  tip: 'Steady and thorough -- Ctrl+E expands tool calls',
  frames: [
    String.raw` (()())
 (o.o )
 (  _ )`,
    String.raw` (()())
 (-.- )
 (  _ )`,
  ],
};

const penguin: AnimalVariant = {
  id: 'penguin',
  name: 'Penguin',
  tip: 'Cool under pressure -- Ctrl+. swaps this mascot',
  frames: [
    String.raw`  (o.o)
  /(  )
  (___)`,
    String.raw`  (-.-)
  /(  )
  (___)`,
  ],
};

export const PIXEL_ANIMALS: AnimalVariant[] = [cat, dog, rabbit, bird, bear, penguin];
