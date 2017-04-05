const path = require('path');

const isProd = process.env.NODE_ENV === 'production';

function getEntrySources() {
  const sources = [];
  if (!isProd) {
    sources.push('webpack-dev-server/client?http://localhost:8080');
  }
  for (let i = 0; i < arguments.length; i++) {
    sources.push(arguments[i]);
  }
  return sources;
}

module.exports = {
  entry: getEntrySources(
    './web/index.js'
  ),
  output: {
    path: path.resolve(__dirname, 'target/webpack'),
    filename: 'bundle.js',
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        exclude: /node_modules/,
        use: ['babel-loader'],
      },
      {
        test: /\.css$/,
        use: ['style-loader', 'css-loader'],
      },
    ]
  },
  devtool: 'source-map',
  devServer: {
    historyApiFallback: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        secure: false
      }
    }
  }
};
