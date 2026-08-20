import 'package:flutter/material.dart';

class PanelCard extends StatelessWidget {
  const PanelCard({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.icon,
    this.actions = const <Widget>[],
    this.padding = const EdgeInsets.all(24),
    this.backgroundColor,
    this.foregroundColor,
    this.subtitleColor,
    this.borderColor,
    this.iconBackgroundColor,
    this.iconColor,
  });

  final String title;
  final String? subtitle;
  final IconData? icon;
  final List<Widget> actions;
  final EdgeInsetsGeometry padding;
  final Color? backgroundColor;
  final Color? foregroundColor;
  final Color? subtitleColor;
  final Color? borderColor;
  final Color? iconBackgroundColor;
  final Color? iconColor;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final resolvedBackground =
        backgroundColor ?? scheme.surface.withValues(alpha: 0.94);
    final resolvedForeground = foregroundColor ?? scheme.onSurface;
    final resolvedSubtitleColor = subtitleColor ?? scheme.onSurfaceVariant;
    final resolvedBorderColor =
        borderColor ?? scheme.outlineVariant.withValues(alpha: 0.44);
    final resolvedIconBackground =
        iconBackgroundColor ?? scheme.primaryContainer.withValues(alpha: 0.82);
    final resolvedIconColor = iconColor ?? scheme.primary;

    return DecoratedBox(
      decoration: BoxDecoration(
        color: resolvedBackground,
        borderRadius: BorderRadius.circular(32),
        border: Border.all(color: resolvedBorderColor),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: scheme.shadow.withValues(alpha: 0.08),
            blurRadius: 28,
            offset: const Offset(0, 18),
          ),
        ],
      ),
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                if (icon != null) ...<Widget>[
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: resolvedIconBackground,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Icon(icon, color: resolvedIconColor),
                  ),
                  const SizedBox(width: 14),
                ],
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        title,
                        style: theme.textTheme.titleLarge?.copyWith(
                          color: resolvedForeground,
                        ),
                      ),
                      if (subtitle != null) ...<Widget>[
                        const SizedBox(height: 4),
                        Text(
                          subtitle!,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: resolvedSubtitleColor,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                if (actions.isNotEmpty)
                  Wrap(spacing: 8, runSpacing: 8, children: actions),
              ],
            ),
            const SizedBox(height: 20),
            DefaultTextStyle.merge(
              style:
                  theme.textTheme.bodyMedium?.copyWith(
                    color: resolvedForeground,
                  ) ??
                  const TextStyle(),
              child: IconTheme.merge(
                data: IconThemeData(color: resolvedForeground),
                child: child,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
