var path = require('path');

function getEntrySources() {
  var sources = [];
  if (process.env.NODE_ENV !== 'production') {
    sources.push('webpack-dev-server/client?http://localhost:8080');
  }
  for (var i = 0; i < arguments.length; i++) {
    sources.push(arguments[i]);
  }
  return sources;
}

module.exports = {
  devtool: 'source-map',
  entry: getEntrySources(
    './web/index.js'
  ),
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
