# Changelog

## [7.1] - 2026-05-04 — Redesign visual completo
### Added
- **Sistema de design** novo (`ui/Design.kt`): primaryGradient, heroGradient (cyan→indigo→violet→magenta), glassCardGradient, ambientGlow, helpers de pulse/scale/glow
- **MeshAmbientBackground**: pano de fundo orgânico com 3 glows animados (cyan, violeta, magenta) que derivam suavemente — base premium para landings
- **Hero gradient** aplicado em títulos principais (CNN logo, Conversores, Identificar, etc) — cores escultorais
- **Skeleton com shimmer aprimorado**: gradiente translúcido de luz por cima do card base, em vez de um shimmer pesado
- Avatar circular com inicial colorida nos cards de conversores (substitui barra fina)
- Mini progress bar inline em cada card de conversor (acompanha 0-4400)
- Card de filtro IA agora tem prefix badge destacado e progress bar gradient
- Card de resultado IA com badge de confiança colorido + título da classe em hero gradient
- Halo radial atrás do botão de captura
- Sweep ring multicolor (cyan→violeta→magenta) no botão da câmera quando em modo rajada

### Changed
- **Paleta refinada**: cyan eletrico (`#22D3EE`), violeta (`#8B5CF6`), magenta (`#EC4899`), indigo (`#6366F1`), success emerald (`#10B981`)
- Superfícies recalibradas: Dark00..Dark40 com tom azul-petróleo profundo, contrastes mais suaves
- **Tipografia escultural**: pesos mais altos (Black, ExtraBold), letter-spacing negativo nos display, line-height otimizado
- Botões primários (Entrar, Abrir Câmera, Adicionar, Novo, Enviar, Confirmar) com **gradient hero** + borda translúcida branca
- Cards (login, conversores, filtros, resultado IA) com **borda gradient sutil** em vez de borda chapada
- Dialogs com `Dark15` (mais elevado) e `RoundedCornerShape(28.dp)` para visual mais soft
- Empty state do ConvertersScreen com halo glow ambiente + ícone duplo (gradient + border)
- Top bar de Convertersrearrumada: título com hero gradient, busca em pill, toggle Fotos/IA full-width
- IdentifyScreen landing redesenhada: mesh ambient background, ícone central com **3 anéis pulsantes** (sweep multicolor + ring concêntrico + halo radial)
- Card de resultado IA agora tem pílula de confiança (cor reativa) e gradient sutil na borda

### Removed
- Background estático plano da IdentifyScreen (substituído por mesh ambiente)
- Spinners simples (substituídos por skeleton boxes em todas as telas relevantes)

## [7.0] - 2026-05-04
### Added
- **PhotoCountCache**: cache persistente de contagens por conversor + estado "completo" (verde) reage imediatamente ao atingir 4400 fotos e persiste entre aberturas (resolve a_resolver.md)
- **IdentificationHistory**: histórico das ultimas 20 identificações da IA, exibido como cards horizontais na landing
- **SettingsScreen**: nova tela de configurações acessível via icone de engrenagem na landing — logout, limpar histórico, limpar cache de imagens, info de versão/build
- Botão de **logout** que apaga credenciais biométricas e sessão
- **Busca de conversores** com filtro em tempo real na ConvertersScreen (ícone de lupa)
- Toggle Fotos/IA agora ocupa toda a largura (mais fácil de tocar)
- **Cancelar uploads** em andamento direto da PhotosScreen
- Indicador de upload com **progresso real** (X de Y) e aviso de "Sem rede · tentando…" quando há retry
- Vibração de sucesso quando IA identifica um conversor
- Texto "X cadastrados · Y completos" no topo da ConvertersScreen
- **Skeleton loading** em ConvertersScreen, PhotosScreen, FilterGridScreen e thumbnails (substitui spinners)
- Componente reutilizável `SkeletonBox` com shimmer animado

### Changed
- **Versão dinâmica** no LoginScreen — substitui "v1.0" hardcoded pelo `versionName` real
- **ApiClient** com hierarquia de exceções tipadas (`ApiError.Network/Timeout/Unauthorized/Forbidden/NotFound/Conflict/BadRequest/Server/Unknown`) e mensagens em português
- **UploadManager** distingue erro permanente (HTTP) de transitório (rede/timeout) e desiste após 10 tentativas em vez de loop infinito
- IdentifyScreen mostra histórico de identificações entre o status do modelo e o botão "Abrir Camera"
- Loading inicial não pisca quando lista já tem dados em cache (mostra dados imediatamente, atualiza no background)

### Fixed
- **a_resolver.md**: pasta agora fica verde imediatamente ao atingir 4400 fotos (cache persistente em SharedPreferences) e mantém o estado entre aberturas; verifica inversa restaura para azul se cair abaixo de 4400
- Renomear/apagar conversor agora sincroniza o `PhotoCountCache` (sem inconsistência de cor após operação)
- Versão exibida no Login estava fixa em "v1.0" mesmo após bumps de versão

## [6.0] - 2026-03-25
### Added
- **IdentifyScreen** — nova tela inicial pos-login com camera fullscreen para identificacao de conversores via IA
- Captura de foto com crop quadrado 512x512, envio para endpoint `/infer` com TTA (test-time augmentation)
- Exibicao de resultado principal (classe + confianca) e **top-5 predicoes** com barras de confianca animadas
- Verificacao de status do modelo na entrada (aviso se modelo nao treinado)
- Crosshair animado no modo de visao da camera
- **Landing page** com icone pulsante, status do modelo (pronto/nao treinado/verificando) e botao "Abrir Camera"
- **Pedido de permissao de camera** com dialogo de retry e redirecionamento para configuracoes se negado permanentemente
- Botao **"Area de Treino"** para acessar a area de coleta/treino (fluxo anterior)
- Novos endpoints no ApiClient: `infer()` (POST /infer) e `inferStatus()` (GET /infer/status)
- Data classes `InferResult` e `InferStatus` no ApiClient

### Changed
- **Navegacao pos-login agora vai para IdentifyScreen** em vez de ConvertersScreen
- Fluxo de navegacao: Login → Identify (landing) → Camera → Converters (via botao "Area de Treino")
- BackHandler atualizado: Converters volta para Identify, Identify volta para Login
- Camera so abre quando usuario clica em "Abrir Camera" (nao mais automaticamente)

## [5.0] - 2026-03-20
### Added
- Opção de **renomear** conversor via long press no card → Renomear
- Opção de **apagar** conversor com diálogo de confirmação via long press no card → Apagar
- Novos métodos no ApiClient: `renameConversor` (PATCH), `deleteConversor` (DELETE)
- Tratamento de erros específicos por código HTTP (404, 409, 400) nos novos endpoints
- **Logo LBWMA** na tela de login com fonte Outfit Bold (Google Fonts)
- FontFamily `OutfitBold` registrada em `Type.kt` para uso global
- **ReviewScreen fase 1 — Grid**: visão geral em 2 colunas com botão X para remover fotos sem confirmação
- Toque na foto do grid abre visualização fullscreen com pinch-to-zoom
- Botões **Apagar** e **Manter** clicáveis na fase de swipe (além do arraste)
- **Auto-finish**: ao atingir o máximo de fotos mantidas, finaliza revisão automaticamente e descarta restantes

### Changed
- Threshold do swipe aumentado de 30% para 40% da tela (menos sensível)
- ReviewScreen agora em duas fases: limpeza rápida por grid → revisão individual por swipe
- Botões Apagar/Manter com estilo de pill (fundo colorido + borda) em vez de texto simples

### Fixed
- Grid de revisão vazio (todas apagadas) agora encerra automaticamente e limpa o store
- **Limpeza de arquivos órfãos** no startup: `RefinementStore.cleanup()` remove entradas stale e apaga fotos órfãs
- Cancelar revisão agora sincroniza o store com os arquivos restantes (não deixa referências de arquivos já deletados)
- **Fotos pendentes agora persistem entre atualizações**: migradas de `cacheDir` (volátil) para `filesDir/pending_review/` (persistente)
- Migração automática de arquivos existentes no `cacheDir` para a nova pasta no primeiro startup após atualização

## [4.8] - Atualização automática
### Added
- Sistema de **auto-update** completo com verificação de versão no servidor
- Overlay fullscreen durante download com barra de progresso e percentual
- FileProvider para instalação segura do APK
- Estados de atualização: checking → available → downloading → installing → error
- Botão de retry em caso de falha no download

## [4.5] - Hub novo e visualização melhorada
### Changed
- Redesign completo da **tela de conversores** (cards com gradient horizontal, barra de acento colorida, contagem de fotos)
- Redesign da **tela de fotos** (grid adaptativo com thumbnails)
- Paleta dark mode refinada (Dark00–Dark30, Cyan40/60/80)

### Added
- Animações staggered na lista (fadeIn + slideUp com spring bounce)
- Pull-to-refresh em todas as listas
- Tema **glass morphism** (bordas semi-transparentes, gradients sutis)
- Barra de acento verde quando conversor atinge 4400 fotos

## [4.0] - Modo IA e sistema de filtros
### Added
- **Modo IA** com toggle Fotos/IA na tela de conversores (persistido em SharedPreferences)
- **16 filtros** predefinidos (f01–f16) com combinações de tampa/pano, bancada/mesa, novo/velho
- **FilterGridScreen** com grid 2 colunas e progresso individual (275 fotos por filtro, 4400 total)
- Indicadores de status: checkmark verde (completo), hourglass âmbar (pendente refinamento)
- **ReviewScreen** com swipe horizontal para manter/descartar fotos capturadas
- **RefinementStore** para persistir fotos pendentes de revisão entre navegações
- Captura em modo IA: crop quadrado automático + redimensionar para 512×512
- Prefixos de filtro nas fotos (f01_timestamp.jpg)
- Limite automático de upload baseado em vagas restantes (275 - server_count)

## [3.5] - Upload concorrente e melhorias de estabilidade
### Added
- **UploadManager** com fila concorrente (máx. 3 uploads simultâneos via Semaphore)
- Retry com backoff exponencial (3s → 6s → 12s → ... → 60s máx)
- SupervisorJob para uploads sobreviverem à navegação entre telas
- Progresso de upload em tempo real (barra linear + contagem pendente/completo)

### Fixed
- Crash ao enviar muitas imagens de uma vez (substituído por fila controlada)

## [3.0] - Câmera e captura
### Added
- **CameraScreen** com CameraX (câmera traseira)
- **Modo burst**: arrastar botão pra cima para captura contínua (380ms intervalo)
- Feedback háptico na captura (vibração 30ms)
- Flash overlay animado (80ms)
- Overlay quadrado com marcadores de canto para modo IA
- Contador de fotos capturadas (badge top-right)
- Botão de enviar aparece quando há fotos capturadas
- Diálogo de confirmação ao descartar fotos não enviadas

## [2.5] - Sistema de thumbnails e visualização
### Added
- **ThumbnailCache**: cache local de miniaturas (300px, JPEG 70%)
- Geração lazy on-demand do servidor + geração local pós-upload
- **FullPhotoScreen** com pinch-to-zoom (1×–5×) e pan
- Carregamento de imagens com **Coil** (memory cache 20% RAM, disk cache 5%)
- Auth interceptor injetado no Coil para imagens protegidas

## [2.0] - Gerenciamento de fotos e conversores
### Added
- **ConvertersScreen**: listar conversores do servidor, criar novo via dialog
- **PhotosScreen**: grid com thumbnails, upload via câmera ou galeria
- Deletar foto com diálogo de confirmação
- FAB com menu dropdown (câmera / galeria)
- Contagem de fotos por conversor carregada em paralelo (async/await)
- Estado vazio com ícone e instrução para criar primeiro conversor

## [1.5] - Autenticação biométrica
### Added
- **Login biométrico** (impressão digital / reconhecimento facial)
- BiometricHelper com política BIOMETRIC_STRONG
- Credenciais salvas com **EncryptedSharedPreferences** (AES256_GCM + MasterKey)
- Auto-trigger biométrico na abertura se há credenciais salvas
- Fallback para login manual se biometria falhar ou não disponível

## [1.0] - Versão inicial
### Added
- **LoginScreen** com HTTP Basic Auth
- Conexão com servidor (`https://server.lbwma.com`)
- ApiClient singleton com OkHttp3 (15s connect, 60s read, 120s write)
- MainActivity com navegação por profundidade e animações slide + fade
- Timeout de sessão (10 min de inatividade → logout automático)
- Tema dark mode base (Jetpack Compose + Material3)
- Permissões: CAMERA, INTERNET, USE_BIOMETRIC, REQUEST_INSTALL_PACKAGES
