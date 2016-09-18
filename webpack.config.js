var path = require('path');

function getEntrySources(sources) {
  if (process.env.NODE_ENV !== 'production') {
    sources.push('webpack-dev-server/client?http://localhost:8080');
  }
  return sources;
}

module.exports = {
  entry: getEntrySources([
    './web/index.js'
  ]),
  output: {
    path: path.resolve(__dirname, 'target/web'),
    filename: 'bundle.js'
  },
  module: {
    loaders: [
      {test: /\.js$/, loaders: ['jsx', 'babel'], exclude: /node_modules/},
      {test: /\.css$/, loader: 'style!css'}
    ]
  }
};
